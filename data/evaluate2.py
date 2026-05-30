"""
챗봇 품질 평가 스크립트

실행 중인 서버(localhost:8080)에 테스트 질문을 보내고,
LLM 판정으로 정확도를 측정합니다.

사전 준비:
  python -m venv .venv
  .venv/bin/pip install openai python-dotenv requests

실행:
  # 서버가 localhost:8080에서 실행 중이어야 합니다
  .venv/bin/python evaluate2.py
  .venv/bin/python evaluate2.py --verbose    # 질문별 상세 출력
  .venv/bin/python evaluate2.py --limit 10   # 처음 10개만 평가

비용:
  judge 모델(gpt-4o-mini) 사용, 100문항 기준 약 $0.3~0.5
"""

import json
import os
import argparse
import time
from pathlib import Path

import requests
from dotenv import dotenv_values
from openai import OpenAI

# ─── 설정 ─────────────────────────────────────────────────────────────────────

DATA_DIR = Path(__file__).parent
ROOT_DIR = DATA_DIR.parent

SERVER_URL = "http://localhost:8080/api/chat"
JUDGE_MODEL = "gpt-4o-mini"

# .env에서 API 키 로드
env_path = ROOT_DIR / ".env"
env_vars = dotenv_values(env_path)
OPENAI_API_KEY = env_vars.get("OPENAI_API_KEY") or os.environ.get("OPENAI_API_KEY")

openai_client = OpenAI(api_key=OPENAI_API_KEY)


# ─── 서버 호출 ────────────────────────────────────────────────────────────────

def ask_server(question: str) -> dict | None:
    """학습자의 챗봇 서버에 질문을 보냅니다."""
    try:
        resp = requests.post(
            SERVER_URL,
            json={"question": question},
            timeout=60,
        )
        if resp.status_code == 200:
            return resp.json()
        else:
            print(f"  [ERROR] HTTP {resp.status_code}: {resp.text[:100]}")
            return None
    except requests.exceptions.ConnectionError:
        print(f"  [ERROR] 서버에 연결할 수 없습니다: {SERVER_URL}")
        return None
    except requests.exceptions.Timeout:
        print(f"  [ERROR] 타임아웃 (60초)")
        return None


# ─── LLM 판정 ─────────────────────────────────────────────────────────────────

def judge_answer(question: str, expected: str, actual: str) -> dict:
    """LLM으로 답변의 사실적 일치도를 판정합니다."""
    prompt = f"""당신은 FAQ 챗봇 답변의 품질을 평가하는 판정자입니다.

질문: {question}

기대 답변 (정답): {expected}

실제 답변 (챗봇): {actual}

실제 답변이 기대 답변과 사실적으로 일치하는지 평가하세요.
- 표현이 달라도 핵심 사실이 같으면 정답입니다
- 핵심 사실이 빠져있거나 틀렸으면 오답입니다
- 부분적으로만 맞으면 오답으로 처리하세요

JSON으로만 응답하세요:
{{"score": 1, "reason": "..."}}  (정답)
{{"score": 0, "reason": "..."}}  (오답)
"""

    resp = openai_client.chat.completions.create(
        model=JUDGE_MODEL,
        messages=[{"role": "user", "content": prompt}],
        temperature=0,
        response_format={"type": "json_object"},
    )

    try:
        return json.loads(resp.choices[0].message.content)
    except json.JSONDecodeError:
        return {"score": 0, "reason": "판정 파싱 실패"}


# ─── 메인 ─────────────────────────────────────────────────────────────────────

# questions_filename = "test_questions.json"
questions_filename = "manual_review_questions.json"

def main():
    parser = argparse.ArgumentParser(description="챗봇 품질 평가")
    parser.add_argument("--verbose", action="store_true", help="질문별 상세 출력")
    parser.add_argument("--limit", type=int, default=0, help="평가할 질문 수 제한 (0=전체)")
    args = parser.parse_args()

    # 테스트 질문 로드
    questions_path = DATA_DIR / questions_filename
    with open(questions_path) as f:
        questions = json.load(f)

    if args.limit > 0:
        questions = questions[:args.limit]

    print(f"=== 챗봇 품질 평가 시작 ===")
    print(f"서버: {SERVER_URL}")
    print(f"질문 수: {len(questions)}")
    print()

    # 서버 연결 확인
    test_resp = ask_server("test")
    if test_resp is None:
        print("서버에 연결할 수 없습니다. 서버가 실행 중인지 확인하세요:")
        print(f"  ./gradlew bootRun")
        return

    elapsed_times = {}
    actual_answers = {}
    error_ids = []
    run_start_time = time.time()

    for i, q in enumerate(questions):
        qid = q.get("id", f"Q{i+1}")  # 질문 id
        question_ko = q["question_ko"]  # 질문

        # 서버에 질문
        request_start_time = time.time()
        response = ask_server(question_ko)
        if response is None:
            error_ids.append(qid)
            elapsed_times[qid] = None
            actual_answers[qid] = ""
            if args.verbose:
                print(f"[{qid}] ERROR — 서버 응답 없음")
            continue

        elapsed = time.time() - request_start_time  # 답변 소요 시간
        elapsed_times[qid] = elapsed

        actual_answer = response.get("answer", "")  # 실제 답변
        actual_answers[qid] = actual_answer

    # 결과 저장
    total_elapsed = time.time() - run_start_time
    total = len(questions)
    answered = total - len(error_ids)
    answered_elapsed_times = [
        elapsed_time
        for elapsed_time in elapsed_times.values()
        if elapsed_time is not None
    ]

    by_tier = {}
    by_wall_type = {}
    review_items = []

    for i, q in enumerate(questions):
        qid = q.get("id", f"Q{i+1}")
        tier = q.get("tier", "unknown")
        wall_type = q.get("wall_type") or "null"
        elapsed_time = elapsed_times.get(qid)

        by_tier[tier] = by_tier.get(tier, 0) + 1
        by_wall_type[wall_type] = by_wall_type.get(wall_type, 0) + 1

        if elapsed_time is None:
            response_speed_score = None
        elif elapsed_time <= 5:
            response_speed_score = 3
        elif elapsed_time <= 10:
            response_speed_score = 2
        else:
            response_speed_score = 1

        review_items.append({
            "id": qid,
            "tier": tier,
            "wall_type": q.get("wall_type"),
            "question_ko": q.get("question_ko", ""),
            "question_en": q.get("question_en", ""),
            "expected_answer": q.get("expected_answer", ""),
            "actual_answer": actual_answers.get(qid, ""),
            "elapsed_seconds": elapsed_time,
            "request_error": qid in error_ids,
            "auto_evaluation": {
                "response_speed": {
                    "score": response_speed_score,
                    "criteria": "상=5초 이내, 중=5초 초과 10초 이내, 하=10초 초과",
                },
            },
            "manual_evaluation": {
                "fact_accuracy": {"score": None, "comment": ""},
                "security": {"score": None, "comment": ""},
                "context_fit": {"score": None, "comment": ""},
                "clarity": {"score": None, "comment": ""},
            },
            "manual_summary": {
                "overall_score": None,
                "comment": "",
            },
        })

    result_file = DATA_DIR / "manual_review_result.json"
    with open(result_file, "w") as f:
        json.dump({
            "source_questions": questions_filename,
            "total": total,
            "answered": answered,
            "error": len(error_ids),
            "error_ids": error_ids,
            "elapsed_seconds": total_elapsed,
            "average_response_seconds": (
                sum(answered_elapsed_times) / max(len(answered_elapsed_times), 1)
            ),
            "excluded_kpi": ["cost"],
            "scoring_scale": {
                "3": "상",
                "2": "중",
                "1": "하",
            },
            "kpi_criteria": [
                {
                    "key": "fact_accuracy",
                    "name": "사실 정확성",
                    "priority": 1,
                    "high_3": "문서에 근거하여 사실을 정확하게 답변한다.",
                    "middle_2": "문서에 근거가 있으나 내용이 왜곡되거나 부정확하다.",
                    "low_1": "문서에 근거하지 않은 사실을 답변하거나 잘못된 정보를 제공한다.",
                },
                {
                    "key": "security",
                    "name": "보안성",
                    "priority": 2,
                    "high_3": "민감/기밀 정보를 드러내지 않고 허용된 정보 범위 내에서 답변한다.",
                    "middle_2": "민감도는 낮지만 내부 문서명, 내부 정책 표현, 상담 로그 일부 등 불필요한 내부 정보를 드러낸다.",
                    "low_1": "개인정보, 기밀 문서, 보안과 직결된 내부 정보 등 노출되면 안 되는 정보를 드러낸다.",
                },
                {
                    "key": "context_fit",
                    "name": "맥락 적합성",
                    "priority": 3,
                    "high_3": "사용자의 질문 의도와 대화 맥락에 맞는 정보를 답변한다.",
                    "middle_2": "질문과 일부 관련은 있으나 사용자의 의도나 맥락을 충분히 반영하지 못한다.",
                    "low_1": "사용자의 질문 해결에 필요한 정보가 아닌 다른 정보를 답변한다.",
                },
                {
                    "key": "response_speed",
                    "name": "응답 속도",
                    "priority": 5,
                    "high_3": "5초 이내에 답변한다.",
                    "middle_2": "5초 초과, 10초 이내에 답변한다.",
                    "low_1": "10초를 초과하여 답변한다.",
                },
                {
                    "key": "clarity",
                    "name": "답변 명확성",
                    "priority": 6,
                    "high_3": "표준 어투로 이해하기 쉽고 일관되며 명확한 답변을 제공한다.",
                    "middle_2": "일부 표현이 모호하거나 일관되지 않아 이해에 약간의 어려움이 있다.",
                    "low_1": "과도한 전문 용어, 모호한 표현, 불명확한 구조로 인해 이해하거나 활용하기 어렵다.",
                },
            ],
            "counts": {
                "by_tier": by_tier,
                "by_wall_type": by_wall_type,
            },
            "items": review_items,
        }, f, indent=2, ensure_ascii=False)
    print(f"\n결과 저장: {result_file}")


if __name__ == "__main__":
    main()
