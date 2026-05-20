"""
챗봇 품질 평가 스크립트

실행 중인 서버(localhost:8080)에 테스트 질문을 보내고,
LLM 판정으로 정확도를 측정합니다.

사전 준비:
  python -m venv .venv
  .venv/bin/pip install openai python-dotenv requests

실행:
  # 서버가 localhost:8080에서 실행 중이어야 합니다
  .venv/bin/python evaluate.py
  .venv/bin/python evaluate.py --verbose    # 질문별 상세 출력
  .venv/bin/python evaluate.py --limit 10   # 처음 10개만 평가

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

def main():
    parser = argparse.ArgumentParser(description="챗봇 품질 평가")
    parser.add_argument("--verbose", action="store_true", help="질문별 상세 출력")
    parser.add_argument("--limit", type=int, default=0, help="평가할 질문 수 제한 (0=전체)")
    args = parser.parse_args()

    # 테스트 질문 로드
    questions_path = DATA_DIR / "test_questions.json"
    with open(questions_path) as f:
        questions = json.load(f)

    if args.limit > 0:
        questions = questions[:args.limit]

    print(f"=== 챗봇 품질 평가 시작 ===")
    print(f"서버: {SERVER_URL}")
    print(f"질문 수: {len(questions)}")
    print(f"판정 모델: {JUDGE_MODEL}")
    print()

    # 서버 연결 확인
    test_resp = ask_server("test")
    if test_resp is None:
        print("서버에 연결할 수 없습니다. 서버가 실행 중인지 확인하세요:")
        print(f"  ./gradlew bootRun")
        return

    results = {"correct": 0, "incorrect": 0, "error": 0}
    tier_results = {}
    start_time = time.time()

    for i, q in enumerate(questions):
        qid = q.get("id", f"Q{i+1}")
        question_ko = q["question_ko"]
        expected = q["expected_answer"]
        tier = q.get("tier", "unknown")

        if tier not in tier_results:
            tier_results[tier] = {"correct": 0, "total": 0}
        tier_results[tier]["total"] += 1

        # 서버에 질문
        response = ask_server(question_ko)
        if response is None:
            results["error"] += 1
            if args.verbose:
                print(f"[{qid}] ERROR — 서버 응답 없음")
            continue

        actual_answer = response.get("answer", "")

        # LLM 판정
        judgment = judge_answer(question_ko, expected, actual_answer)
        score = judgment.get("score", 0)

        if score == 1:
            results["correct"] += 1
            tier_results[tier]["correct"] += 1
            marker = "✓"
        else:
            results["incorrect"] += 1
            marker = "✗"

        if args.verbose:
            print(f"[{qid}] {marker} ({tier}) {question_ko[:40]}...")
            if score == 0:
                print(f"        이유: {judgment.get('reason', '')[:160]}")

        # 진행률 (10개마다)
        if not args.verbose and (i + 1) % 10 == 0:
            print(f"  진행: {i+1}/{len(questions)}")

    # 결과 출력
    elapsed = time.time() - start_time
    total = results["correct"] + results["incorrect"] + results["error"]

    print()
    print(f"=== 평가 결과 ===")
    print(f"전체: {results['correct']}/{total} ({results['correct']/max(total,1)*100:.1f}%)")
    print()

    print("난이도별:")
    for tier in sorted(tier_results.keys()):
        t = tier_results[tier]
        pct = t["correct"] / max(t["total"], 1) * 100
        print(f"  {tier:8s}: {t['correct']:2d}/{t['total']:2d} ({pct:.0f}%)")

    if results["error"] > 0:
        print(f"\n  에러: {results['error']}건")

    print(f"\n소요 시간: {elapsed:.1f}초")
    print(f"평균 응답: {elapsed/max(total,1):.1f}초/질문")

    # 결과 저장
    result_file = DATA_DIR / "eval_result.json"
    with open(result_file, "w") as f:
        json.dump({
            "total": total,
            "correct": results["correct"],
            "incorrect": results["incorrect"],
            "error": results["error"],
            "accuracy": results["correct"] / max(total, 1),
            "tier_results": tier_results,
            "elapsed_seconds": elapsed,
        }, f, indent=2, ensure_ascii=False)
    print(f"\n결과 저장: {result_file}")


if __name__ == "__main__":
    main()
