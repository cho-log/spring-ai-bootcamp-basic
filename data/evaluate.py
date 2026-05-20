"""
챗봇 품질 평가 스크립트

실행 중인 서버(localhost:8080)에 테스트 질문을 보내고,
LLM 판정으로 KPI 지표를 측정합니다.

KPI 지표:
  core_response  핵심 응답 성공  — 질문이 요구하는 정보에 직접 답변했는가
  factuality     사실성          — 기대 답변과 모순되는 내용이 없는가
  front_loaded   두괄식 응답     — 첫 문장에 직접 답변이 있는가
  restraint      정보 절제력     — 부가 정보(논리 단위)가 1개 이하인가
  conciseness    간결성          — 응답이 200자 이하인가 (Python 계산)
  final_pass     최종 통과       — core_response × factuality

사전 준비:
  python -m venv .venv
  .venv/bin/pip install openai python-dotenv requests

실행:
  # 서버가 localhost:8080에서 실행 중이어야 합니다
  .venv/bin/python evaluate.py
  .venv/bin/python evaluate.py --verbose       # 질문별 상세 출력
  .venv/bin/python evaluate.py --limit 10      # 처음 10개만 평가
  .venv/bin/python evaluate.py --parallel 10   # 병렬 워커 10개로 가속

비용:
  judge 모델(gpt-4o-mini) 사용, 100문항 기준 약 $0.3~0.5
"""

import json
import os
import argparse
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

import requests
from dotenv import dotenv_values
from openai import OpenAI

# ─── 설정 ─────────────────────────────────────────────────────────────────────

DATA_DIR = Path(__file__).parent
ROOT_DIR = DATA_DIR.parent

SERVER_URL = "http://localhost:8080/api/chat"
JUDGE_MODEL = "gpt-4o-mini"
CONCISENESS_MAX_CHARS = 200

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
    """LLM으로 4개 KPI 지표를 단일 호출로 판정합니다."""
    prompt = f"""당신은 챗봇 답변 품질을 평가하는 판정자입니다.

[질문]: {question}
[기대 답변]: {expected}
[실제 답변]: {actual}

아래 4개 지표를 각각 판정하세요.

1. core_response — [질문]이 요구하는 정보에 실제 답변이 직접 응답했는가?
  [질문]에서 사용자가 묻는 것(수치·기간·조건·방법 등)을 먼저 파악하세요.
  [기대 답변]은 정답 팩트 확인 기준으로만 사용하세요.
  1: 질문이 요구하는 정보에 직접 답변하며 [기대 답변]의 사실과 일치
  0: 질문에 대한 답변 누락·오류, 또는 기대 답변이 있는데 거절한 경우

2. factuality — 실제 답변 전체에 [기대 답변]과 모순되는 사실이 없는가?
  1: [질문]에 대한 답변과 부가 정보 모두 [기대 답변]과 모순 없음
  1: 거절 응답 (허위 정보 없음)
  0: [기대 답변]의 사실과 충돌하는 내용 포함

3. front_loaded — 첫 문장에 [질문]에 대한 직접 답변이 있는가?
  1: 첫 문장에 질문이 요구하는 정보를 직접 전달
  1: 거절 응답 (거절 의사가 첫 문장에 명확히 표현)
  0: 서론·공감·확인 문구("안녕하세요", "좋은 질문이에요" 등)로 시작

4. restraint — [질문]에 대한 직접 답변 외 부가 정보(논리 단위)가 1개 이하인가?
  [질문]이 여러 항목을 묻는 경우, 각 항목의 답변은 직접 답변으로 간주 (부가 집계 제외)
  1: 부가 정보 1개 이하
  0: 부가 정보 2개 이상

JSON으로만 응답:
{{"core_response":1,"factuality":1,"front_loaded":1,"restraint":1,"reasons":{{"core_response":"...","factuality":"...","front_loaded":"...","restraint":"..."}}}}"""

    resp = openai_client.chat.completions.create(
        model=JUDGE_MODEL,
        messages=[{"role": "user", "content": prompt}],
        temperature=0,
        response_format={"type": "json_object"},
    )

    usage = resp.usage
    try:
        result = json.loads(resp.choices[0].message.content)
    except json.JSONDecodeError:
        result = {
            "core_response": 0, "factuality": 0, "front_loaded": 0, "restraint": 0,
            "reasons": {k: "판정 파싱 실패" for k in ("core_response", "factuality", "front_loaded", "restraint")},
        }

    result["judge_usage"] = {
        "prompt_tokens": usage.prompt_tokens,
        "completion_tokens": usage.completion_tokens,
        "total_tokens": usage.total_tokens,
    }
    return result


# ─── 워커 ─────────────────────────────────────────────────────────────────────

def process_question(q: dict, idx: int) -> dict:
    """질문 1건을 처리해 결과 dict를 반환합니다. (스레드 안전)"""
    start = time.time()
    qid = q.get("id", f"Q{idx+1}")
    question_ko = q["question_ko"]
    expected = q["expected_answer"]
    tier = q.get("tier", "unknown")

    response = ask_server(question_ko)
    if response is None:
        return {"qid": qid, "tier": tier, "status": "error", "question": question_ko,
                "token_usage": {}, "duration": time.time() - start}

    actual_answer = response.get("answer", "")
    token_usage = response.get("tokenUsage", {})
    judgment = judge_answer(question_ko, expected, actual_answer)

    core_response = judgment.get("core_response", 0)
    factuality = judgment.get("factuality", 0)

    return {
        "qid": qid,
        "tier": tier,
        "status": "ok",
        "question": question_ko,
        "token_usage": token_usage,
        "duration": time.time() - start,
        "kpi": {
            "core_response": core_response,
            "factuality": factuality,
            "front_loaded": judgment.get("front_loaded", 0),
            "restraint": judgment.get("restraint", 0),
            "conciseness": int(len(actual_answer) <= CONCISENESS_MAX_CHARS),
            "final_pass": core_response * factuality,
        },
        "reasons": judgment.get("reasons", {}),
    }


# ─── 메인 ─────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="챗봇 품질 평가")
    parser.add_argument("--verbose", action="store_true", help="질문별 상세 출력")
    parser.add_argument("--limit", type=int, default=0, help="평가할 질문 수 제한 (0=전체)")
    parser.add_argument("--parallel", type=int, default=1, help="병렬 워커 수 (default: 1, 순차 실행)")
    args = parser.parse_args()

    questions_path = DATA_DIR / "test_questions.json"
    with open(questions_path) as f:
        questions = json.load(f)

    if args.limit > 0:
        questions = questions[:args.limit]

    print(f"=== 챗봇 품질 평가 시작 ===")
    print(f"서버: {SERVER_URL}")
    print(f"질문 수: {len(questions)}")
    print(f"판정 모델: {JUDGE_MODEL}")
    if args.parallel > 1:
        print(f"병렬 워커: {args.parallel}")
    print()

    test_resp = ask_server("test")
    if test_resp is None:
        print("서버에 연결할 수 없습니다. 서버가 실행 중인지 확인하세요:")
        print(f"  ./gradlew bootRun")
        return

    error_count = 0
    kpi_totals = {k: 0 for k in ("core_response", "factuality", "front_loaded", "restraint", "conciseness", "final_pass")}
    tier_results = {}
    chatbot_usage = {"prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0}
    durations = []
    start_time = time.time()

    def handle_result(r):
        nonlocal error_count
        _aggregate(r, kpi_totals, tier_results, chatbot_usage, durations, args.verbose)
        if r["status"] == "error":
            error_count += 1

    # ─── 실행 (순차 / 병렬 공통 집계) ────────────────────────────────────────
    if args.parallel > 1:
        with ThreadPoolExecutor(max_workers=args.parallel) as executor:
            futures = [executor.submit(process_question, q, i) for i, q in enumerate(questions)]
            for completed, fut in enumerate(as_completed(futures), 1):
                handle_result(fut.result())
                if not args.verbose and completed % 10 == 0:
                    print(f"  진행: {completed}/{len(questions)}")
    else:
        for i, q in enumerate(questions):
            handle_result(process_question(q, i))
            if not args.verbose and (i + 1) % 10 == 0:
                print(f"  진행: {i+1}/{len(questions)}")

    # ─── 결과 출력 ────────────────────────────────────────────────────────────
    elapsed = time.time() - start_time
    total = len(questions)
    evaluated = total - error_count

    print()
    print(f"=== KPI 결과 ({total}문항) ===")
    kpi_labels = [
        ("core_response", "핵심 응답 성공"),
        ("factuality",    "사실성        "),
        ("front_loaded",  "두괄식 응답   "),
        ("restraint",     "정보 절제력   "),
        ("conciseness",   f"간결성 ≤{CONCISENESS_MAX_CHARS}자  "),
    ]
    for key, label in kpi_labels:
        n = kpi_totals[key]
        pct = n / max(evaluated, 1) * 100
        print(f"  {label}: {n:3d}/{evaluated} ({pct:.1f}%)")
    print(f"  {'─' * 36}")
    n = kpi_totals["final_pass"]
    pct = n / max(evaluated, 1) * 100
    print(f"  최종 통과 (정확성)  : {n:3d}/{evaluated} ({pct:.1f}%)")

    print()
    print("난이도별 (최종 통과):")
    for tier in sorted(tier_results.keys()):
        t = tier_results[tier]
        pct = t["correct"] / max(t["total"], 1) * 100
        print(f"  {tier:8s}: {t['correct']:2d}/{t['total']:2d} ({pct:.0f}%)")

    if error_count > 0:
        print(f"\n  에러: {error_count}건")

    print(f"\n소요 시간: {elapsed:.1f}초")
    if durations:
        print(f"평균 응답: {sum(durations)/len(durations):.1f}초/질문")

    print(f"\n=== 챗봇 토큰 사용량 ===")
    print(f"  prompt    : 합계 {chatbot_usage['prompt_tokens']:,} / 평균 {chatbot_usage['prompt_tokens']//max(evaluated,1):,}")
    print(f"  completion: 합계 {chatbot_usage['completion_tokens']:,} / 평균 {chatbot_usage['completion_tokens']//max(evaluated,1):,}")
    print(f"  total     : 합계 {chatbot_usage['total_tokens']:,} / 평균 {chatbot_usage['total_tokens']//max(evaluated,1):,}")

    result_file = DATA_DIR / "eval_result.json"
    with open(result_file, "w") as f:
        json.dump({
            "total": total,
            "correct": kpi_totals["final_pass"],
            "incorrect": evaluated - kpi_totals["final_pass"],
            "error": error_count,
            "accuracy": round(kpi_totals["final_pass"] / max(evaluated, 1), 4),
            "kpi": {
                key: {
                    "correct": kpi_totals[key],
                    "total": evaluated,
                    "rate": round(kpi_totals[key] / max(evaluated, 1), 4),
                }
                for key in ("core_response", "factuality", "front_loaded", "restraint", "conciseness", "final_pass")
            },
            "tier_results": tier_results,
            "elapsed_seconds": elapsed,
            "avg_response_seconds": (sum(durations) / len(durations)) if durations else 0,
            "chatbot_token_usage": chatbot_usage,
        }, f, indent=2, ensure_ascii=False)
    print(f"\n결과 저장: {result_file}")


def _aggregate(r: dict, kpi_totals: dict, tier_results: dict, chatbot_usage: dict,
               durations: list, verbose: bool):
    """process_question 결과 1건을 집계합니다."""
    tier = r["tier"]
    durations.append(r["duration"])

    if tier not in tier_results:
        tier_results[tier] = {"correct": 0, "total": 0}
    tier_results[tier]["total"] += 1

    if r["status"] == "error":
        if verbose:
            print(f"[{r['qid']}] ERROR — 서버 응답 없음")
        return

    token_usage = r["token_usage"]
    chatbot_usage["prompt_tokens"] += token_usage.get("promptTokens", 0)
    chatbot_usage["completion_tokens"] += token_usage.get("completionTokens", 0)
    chatbot_usage["total_tokens"] += token_usage.get("totalTokens", 0)

    kpi = r["kpi"]
    for key in kpi_totals:
        kpi_totals[key] += kpi[key]

    if kpi["final_pass"] == 1:
        tier_results[tier]["correct"] += 1

    if verbose:
        marker = "✓" if kpi["final_pass"] == 1 else "✗"
        print(f"[{r['qid']}] {marker} ({tier}) {r['question'][:40]}...")
        if kpi["final_pass"] == 0:
            for k, v in r.get("reasons", {}).items():
                if kpi.get(k, 1) == 0:
                    print(f"        [{k}=0] {str(v)[:80]}")


if __name__ == "__main__":
    main()
