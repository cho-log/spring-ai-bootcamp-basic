"""
도메인 정책 엄격 평가 스크립트

evaluate.py 와 달리 LLM judge 를 쓰지 않습니다.
정답에 반드시 포함되어야 하는 정규식 패턴(must_contain) 과
절대 등장해서는 안 되는 패턴(must_not_contain) 으로 결정적으로 판정합니다.

같은 답변에 대해 항상 같은 결과를 내므로 프롬프트/RAG 변경의 효과를
노이즈 없이 측정할 수 있습니다.

사전 준비:
  python -m venv .venv
  .venv/bin/pip install requests

실행:
  .venv/bin/python strict_evaluate.py
  .venv/bin/python strict_evaluate.py --verbose
  .venv/bin/python strict_evaluate.py --parallel 10
  .venv/bin/python strict_evaluate.py --category cold_chain
  .venv/bin/python strict_evaluate.py --category adversarial --verbose
"""

import json
import re
import argparse
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

import requests

DATA_DIR = Path(__file__).parent
SERVER_URL = "http://localhost:11240/api/chat"


def ask_server(question: str) -> dict | None:
    try:
        resp = requests.post(SERVER_URL, json={"question": question}, timeout=60)
        if resp.status_code == 200:
            return resp.json()
        print(f"  [ERROR] HTTP {resp.status_code}: {resp.text[:100]}")
        return None
    except requests.exceptions.ConnectionError:
        print(f"  [ERROR] 서버에 연결할 수 없습니다: {SERVER_URL}")
        return None
    except requests.exceptions.Timeout:
        print(f"  [ERROR] 타임아웃 (60초)")
        return None


def check_answer(answer: str, q: dict) -> dict:
    """정규식 패턴으로 결정적으로 검증합니다.

    must_contain: 모든 패턴이 매칭되어야 정답
    must_not_contain: 하나라도 매칭되면 오답
    """
    failures = []

    for pattern in q.get("must_contain", []):
        if not re.search(pattern, answer, re.IGNORECASE | re.DOTALL):
            failures.append(f"누락 must_contain: /{pattern}/")

    for pattern in q.get("must_not_contain", []):
        if re.search(pattern, answer, re.IGNORECASE | re.DOTALL):
            failures.append(f"금지 패턴 등장 must_not_contain: /{pattern}/")

    return {
        "score": 1 if not failures else 0,
        "failures": failures,
    }


def process_question(q: dict) -> dict:
    start = time.time()
    response = ask_server(q["question"])

    if response is None:
        return {
            "qid": q["id"],
            "category": q["category"],
            "status": "error",
            "question": q["question"],
            "duration": time.time() - start,
        }

    actual = response.get("answer", "")
    result = check_answer(actual, q)

    return {
        "qid": q["id"],
        "category": q["category"],
        "status": "ok",
        "score": result["score"],
        "failures": result["failures"],
        "question": q["question"],
        "expected": q.get("expected", ""),
        "answer": actual,
        "reference": q.get("reference", {}),
        "duration": time.time() - start,
    }


def main():
    parser = argparse.ArgumentParser(description="도메인 정책 엄격 평가")
    parser.add_argument("--verbose", action="store_true", help="질문별 상세 출력")
    parser.add_argument("--limit", type=int, default=0, help="평가할 질문 수 제한")
    parser.add_argument("--parallel", type=int, default=1, help="병렬 워커 수")
    parser.add_argument("--category", type=str, default="", help="특정 카테고리만 실행")
    parser.add_argument("--show-answer", action="store_true", help="실패 시 실제 답변 출력")
    args = parser.parse_args()

    questions_path = DATA_DIR / "strict_questions.json"
    with open(questions_path) as f:
        questions = json.load(f)

    if args.category:
        questions = [q for q in questions if q["category"] == args.category]

    if args.limit > 0:
        questions = questions[: args.limit]

    print("=== 도메인 정책 엄격 평가 ===")
    print(f"서버: {SERVER_URL}")
    print(f"질문 수: {len(questions)}")
    if args.parallel > 1:
        print(f"병렬 워커: {args.parallel}")
    print()

    # 서버 연결 확인
    test_resp = ask_server("test")
    if test_resp is None:
        print("서버에 연결할 수 없습니다. 서버가 실행 중인지 확인하세요:")
        print(f"  ./gradlew bootRun")
        return

    cat_results: dict = {}
    total_correct = 0
    total_count = 0
    error_count = 0
    durations: list = []
    detail_results: list = []
    start_time = time.time()

    def collect(r: dict, completed_idx: int, total: int):
        nonlocal total_correct, total_count, error_count
        durations.append(r["duration"])
        cat = r["category"]
        cat_results.setdefault(cat, {"correct": 0, "total": 0})

        if r["status"] == "error":
            error_count += 1
            if args.verbose:
                print(f"[{r['qid']}] ERROR — 서버 응답 없음")
            return

        cat_results[cat]["total"] += 1
        total_count += 1
        if r["score"] == 1:
            cat_results[cat]["correct"] += 1
            total_correct += 1
            marker = "✓"
        else:
            marker = "✗"

        if args.verbose:
            print(f"[{r['qid']}] {marker} ({cat:12s}) {r['question'][:50]}")
            if r["score"] == 0:
                for f in r["failures"]:
                    print(f"        {f}")
                ref = r["reference"]
                if isinstance(ref, dict):
                    print(f"        근거: {ref.get('file', '')}:{ref.get('line', '')}")
                    if ref.get("quote"):
                        print(f"        원문: {ref['quote'][:100]}")
                if args.show_answer:
                    print(f"        답변: {r['answer'][:200]}")

        detail_results.append(r)

        if not args.verbose and completed_idx % 10 == 0:
            print(f"  진행: {completed_idx}/{total}")

    if args.parallel > 1:
        with ThreadPoolExecutor(max_workers=args.parallel) as executor:
            futures = [executor.submit(process_question, q) for q in questions]
            completed = 0
            for fut in as_completed(futures):
                r = fut.result()
                completed += 1
                collect(r, completed, len(questions))
    else:
        for i, q in enumerate(questions):
            r = process_question(q)
            collect(r, i + 1, len(questions))

    elapsed = time.time() - start_time

    print()
    print("=== 평가 결과 ===")
    pct = total_correct / max(total_count, 1) * 100
    print(f"전체: {total_correct}/{total_count} ({pct:.1f}%)")
    print()
    print("카테고리별:")
    for cat in sorted(cat_results.keys()):
        c = cat_results[cat]
        cp = c["correct"] / max(c["total"], 1) * 100
        print(f"  {cat:14s}: {c['correct']:2d}/{c['total']:2d} ({cp:3.0f}%)")

    if error_count > 0:
        print(f"\n  에러: {error_count}건")

    print(f"\n벽시계 시간: {elapsed:.1f}초")
    if durations:
        print(f"평균 응답: {sum(durations)/len(durations):.1f}초/질문")

    result_file = DATA_DIR / "strict_eval_result.json"
    with open(result_file, "w") as f:
        json.dump(
            {
                "total": total_count,
                "correct": total_correct,
                "error": error_count,
                "accuracy": total_correct / max(total_count, 1),
                "by_category": cat_results,
                "elapsed_seconds": elapsed,
                "details": detail_results,
            },
            f,
            indent=2,
            ensure_ascii=False,
        )
    print(f"\n결과 저장: {result_file}")

    # 실패 케이스 요약
    failures = [d for d in detail_results if d.get("score") == 0]
    if failures and not args.verbose:
        print(f"\n실패 케이스 {len(failures)}건 — --verbose 로 상세 확인")
        for d in failures[:5]:
            print(f"  [{d['qid']}] ({d['category']}) {d['question'][:60]}")


if __name__ == "__main__":
    main()
