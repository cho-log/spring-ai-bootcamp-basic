"""
챗봇 품질 평가 스크립트

실행 중인 서버(localhost:8080)에 테스트 질문을 보내고,
LLM 판정으로 KPI 지표를 측정합니다.

KPI 지표:
  refusal_quality 거절 품질       — 거절이 필요한 상황에서 적절히 거절했는가 (0/1/2)
  core_response   핵심 응답 성공   — 질문이 요구하는 정보에 직접 답변했는가 (0/1/2)
  factuality      사실성           — 기대 답변과 모순되는 내용이 없는가 (0/1/2)
  front_loaded    두괄식 응답      — 첫 문장에 직접 답변이 있는가 (0/1)
  restraint       정보 절제력      — 부가 정보(논리 단위)가 1개 이하인가 (0/1)
  conciseness     간결성           — 응답이 200자 이하인가 (Python 계산, 0/1)
  final_pass      최종 통과        — refusal_quality=1 → 통과
                                     refusal_quality=0 → 탈락
                                     refusal_quality=2 → core_response + factuality == 4

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
    """LLM으로 KPI 지표를 단일 호출로 판정합니다."""
    prompt = f"""당신은 챗봇 답변 품질을 평가하는 판정자입니다.

[질문]: {question}
[기대 답변]: {expected}
[실제 답변]: {actual}

아래 5개 지표를 각각 독립적으로 판정하세요.

1. refusal_quality — 거절 여부와 적절성
  2: 거절 없음 (정상 답변)
  1: [기대 답변]이 비어 있거나 내용이 정보 없음 이고 [실제 답변]도 적절히 거절
  0: [기대 답변]에 답변 내용이 있는데 [실제 답변]이 거절

2. core_response — 질문이 요구하는 정보에 직접 답했는가?
  2: 질문의 핵심 정보에 완전히 답변하며 [기대 답변] 사실과 일치
     - 질문이 수치를 묻는 경우 정확한 수치를 답변
       (예: "반품 기간이 며칠이에요?" → "14일입니다"라고 답변)
     - 질문이 조건을 묻는 경우 조건과 적용 대상을 정확히 답변
       (예: "VIP도 배송비 내야 해요?" → "VIP는 무료입니다"라고 답변)
     - 질문이 방법을 묻는 경우 구체적인 절차를 답변
       (예: "반품 어떻게 해요?" → 반품 신청 경로와 절차를 답변)
  1: 질문에 답변했으나 핵심 정보 일부 누락
     - 수치는 맞으나 적용 조건을 빠뜨림
       (예: "언제 환불돼요?" → "3~5일 걸립니다"라고만 답변, 결제수단별 차이 미언급)
     - 방법은 맞으나 핵심 단계 일부 누락
       (예: 반품 절차 안내 시 사진 첨부 단계 누락)
  0: 아래 중 하나에 해당
     - 질문이 요구하는 정보를 전혀 제공하지 않음
     - 질문과 무관한 내용만 답변
     - "고객센터에 문의하세요"처럼 답변을 회피


3. factuality — 실제 답변이 [기대 답변]과 모순되는 내용이 없는가?
  수치뿐 아니라 조건·논리·인과관계도 포함하여 검토하세요.

  2: 답변 내 모든 내용이 기대 답변과 일치
     - 수치·기간이 정확히 같음
       (예: 반품 기간 14일 → "14일"이라고 답변)
     - 조건과 적용 대상이 정확히 같음
       (예: "VIP만 무료" → "VIP만 무료"라고 답변)
     - 인과관계가 정확히 같음 — 원인과 결과를 모두 포함
       (예: "반품하면 포인트 차감" → "반품 시 포인트가 차감됩니다"라고 답변)
       ※ 결과만 언급하고 원인 조건을 빠뜨리면 1점
  1: 질문에 대한 직접적인 핵심 답변은 맞으나 부가 설명이 기대 답변과 다른 내용 포함
        - Q: "반품 기간이 며칠이에요?"
          핵심 답변(반품 기간): "14일입니다" → 맞음
          부가 설명(틀린 정보): "단, 냉장 상품은 7일입니다" → 기대 답변에 없는 틀린 정보

        - Q: "VIP는 배송비 무료인가요?"
          핵심 답변(VIP 배송비): "네, 무료입니다" → 맞음
          부가 설명(틀린 정보): "단, 구독 중인 경우에만 적용됩니다" → 기대 답변과 다른 조건 추가

        - Q: "포인트 적립률이 몇 퍼센트예요?"
          핵심 답변(적립률): "3%입니다" → 맞음
          부가 설명(틀린 정보): "VIP 회원은 10% 적립됩니다" → 기대 답변의 수치와 다름
  0: 아래 중 하나에 해당
     - 수치·기간이 기대 답변과 다름
       (예: 반품 기간 14일 → "7일"이라고 답변)
     - 조건·적용 대상이 반전됨
       (예: "VIP만 무료" → "모든 회원 무료"라고 답변)
     - 인과관계가 반전됨
       (예: "반품하면 등급 하락" → "등급에 영향 없다"고 답변)
     - 기대 답변에 없는 구체적 수치·정책을 만들어서 답변


4. front_loaded — 첫 문장에 [질문]에 대한 직접 답변이 있는가?
  1: 첫 문장에 질문이 요구하는 정보를 직접 전달
  1: 거절 응답 (거절 의사가 첫 문장에 명확히 표현)
  0: 서론·공감·확인 문구("안녕하세요", "좋은 질문이에요" 등)로 시작

5. restraint — [질문]에 대한 직접 답변 외 부가 정보가 1개 이하인가?
  [질문]이 여러 항목을 묻는 경우, 각 항목의 답변은 직접 답변으로 간주 (부가 집계 제외)
  1: 부가 정보 1개 이하
  0: 부가 정보 2개 이상

JSON으로만 응답:
{{"refusal_quality":2,"core_response":2,"factuality":2,"front_loaded":1,"restraint":1,"reasons":{{"refusal_quality":"...","core_response":"...","factuality":"...","front_loaded":"...","restraint":"..."}}}}"""

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
            "refusal_quality": 0, "core_response": 0, "factuality": 0,
            "front_loaded": 0, "restraint": 0,
            "reasons": {k: "판정 파싱 실패" for k in
                        ("refusal_quality", "core_response", "factuality", "front_loaded", "restraint")},
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

    refusal_quality = judgment.get("refusal_quality", 0)
    core_response   = judgment.get("core_response", 0)
    factuality      = judgment.get("factuality", 0)

    if refusal_quality == 1:
        final_pass = 1
    elif refusal_quality == 0:
        final_pass = 0
    else:  # refusal_quality == 2 (정상 답변)
        final_pass = int(core_response + factuality == 4)

    return {
        "qid": qid,
        "tier": tier,
        "status": "ok",
        "question": question_ko,
        "token_usage": token_usage,
        "duration": time.time() - start,
        "kpi": {
            "refusal_quality": refusal_quality,
            "core_response":   core_response,
            "factuality":      factuality,
            "front_loaded":    judgment.get("front_loaded", 0),
            "restraint":       judgment.get("restraint", 0),
            "conciseness":     int(len(actual_answer) <= CONCISENESS_MAX_CHARS),
            "final_pass":      final_pass,
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
    kpi_totals = {
        "refusal_quality": {"score_0": 0, "score_1": 0, "score_2": 0},
        "core_response":   {"score_0": 0, "score_1": 0, "score_2": 0},
        "factuality":      {"score_0": 0, "score_1": 0, "score_2": 0},
        "front_loaded":    {"score_0": 0, "score_1": 0},
        "restraint":       {"score_0": 0, "score_1": 0},
        "conciseness":     {"score_0": 0, "score_1": 0},
        "final_pass":      {"score_0": 0, "score_1": 0},
    }
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

    def pct(n): return n / max(evaluated, 1) * 100

    print()
    print(f"=== KPI 결과 ({total}문항) ===")

    # 0/1/2 지표: 분포 출력
    for key, label in [
        ("refusal_quality", "거절 품질      "),
        ("core_response",   "핵심 응답 성공  "),
        ("factuality",      "사실성          "),
    ]:
        t = kpi_totals[key]
        print(f"  {label}: 완전 {t['score_2']}({pct(t['score_2']):.0f}%) | 부분 {t['score_1']}({pct(t['score_1']):.0f}%) | 실패 {t['score_0']}({pct(t['score_0']):.0f}%)")

    # 0/1 지표: 통과/실패 출력
    for key, label in [
        ("front_loaded", "두괄식 응답     "),
        ("restraint",    "정보 절제력     "),
        ("conciseness",  f"간결성 ≤{CONCISENESS_MAX_CHARS}자   "),
    ]:
        t = kpi_totals[key]
        print(f"  {label}: 통과 {t['score_1']}({pct(t['score_1']):.1f}%) | 실패 {t['score_0']}({pct(t['score_0']):.1f}%)")

    print(f"  {'─' * 36}")
    fp = kpi_totals["final_pass"]
    print(f"  최종 통과 (정확성)  : {fp['score_1']:3d}/{evaluated} ({pct(fp['score_1']):.1f}%)")

    print()
    print("난이도별 (최종 통과):")
    for tier in sorted(tier_results.keys()):
        t = tier_results[tier]
        p = t["correct"] / max(t["total"], 1) * 100
        print(f"  {tier:8s}: {t['correct']:2d}/{t['total']:2d} ({p:.0f}%)")

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
            "correct": kpi_totals["final_pass"]["score_1"],
            "incorrect": evaluated - kpi_totals["final_pass"]["score_1"],
            "error": error_count,
            "accuracy": round(kpi_totals["final_pass"]["score_1"] / max(evaluated, 1), 4),
            "kpi": {
                key: {**kpi_totals[key], "total": evaluated}
                for key in kpi_totals
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
        score = kpi[key]
        kpi_totals[key][f"score_{score}"] += 1

    if kpi["final_pass"] == 1:
        tier_results[tier]["correct"] += 1

    if verbose:
        marker = "✓" if kpi["final_pass"] == 1 else "✗"
        print(f"[{r['qid']}] {marker} ({tier}) {r['question'][:40]}...")
        if kpi["final_pass"] == 0:
            for k, v in r.get("reasons", {}).items():
                if kpi.get(k, 2) == 0:
                    print(f"        [{k}=0] {str(v)[:80]}")


if __name__ == "__main__":
    main()
