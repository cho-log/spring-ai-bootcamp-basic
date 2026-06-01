import json
import os
import argparse
import time
from pathlib import Path

from datasets import Dataset
from ragas import evaluate
from ragas.metrics import (
    Faithfulness,
    AnswerRelevancy,
    ContextPrecision,
    ContextRelevance,
)

from langchain_openai import ChatOpenAI
from ragas.llms import LangchainLLMWrapper

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


# ─── ragas 평가 ─────────────────────────────────────────────────────────────────────

def evaluatee():
    source_file = DATA_DIR / "wall_type_null_test_results.json"
    result_file = DATA_DIR / "wall_type_null_test_results.json"

    with open(source_file, "r", encoding="utf-8") as f:
        items = json.load(f)

    questions = []
    answers = []
    contexts = []
    ground_truths = []

    for item in items:
        questions.append(item["question"])
        answers.append(item["answer"])
        contexts.append(item["contexts"])

    data = {
        "question": questions,
        "answer": answers,
        "contexts": contexts,
    }
    dataset = Dataset.from_dict(data)

    base_llm = ChatOpenAI(
        model="gpt-4o-mini",
        temperature=0,
    )
    ragas_llm = LangchainLLMWrapper(base_llm)


    return evaluate(
        dataset,
        metrics=[
                Faithfulness(),
                AnswerRelevancy(),
#                 ContextPrecision(),
                ContextRelevance(),
        ],
        llm=ragas_llm,
    )

# ─── 메인 ─────────────────────────────────────────────────────────────────────

questions_filename = "wall_type_null_test_questions.json"

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

    items = []
    error_ids = []
    run_start_time = time.time()

    for i, q in enumerate(questions):
        qid = q.get("id", f"Q{i+1}")  # 질문 id
        question_ko = q["question_ko"]  # 질문

        # 서버에 질문
        response = ask_server(question_ko)
        if response is None:
            error_ids.append(qid)
            elapsed_times[qid] = None
            actual_answers[qid] = ""
            if args.verbose:
                print(f"[{qid}] ERROR — 서버 응답 없음")
            continue

        answer = response.get("answer", "")  # 실제 답변
        contexts = response.get("contexts", "")  # 검색 컨텍스트(리스트)

        item = {
            "id": qid,
            "question": question_ko,
            "answer": answer,
            "contexts": contexts
        }
        items.append(item)

    # 챗봇 호출 결과 저장
    total_elapsed = time.time() - run_start_time
    total = len(questions)
    answered = total - len(error_ids)

    result_file = DATA_DIR / "wall_type_null_test_results.json"
    with open(result_file, "w") as f:
        json.dump(items, f, indent=2, ensure_ascii=False)
    print(f"\n결과 저장: {result_file}")

    # ragas 평가
    result = evaluatee()
    print(result)



if __name__ == "__main__":
    main()
