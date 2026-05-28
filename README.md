# 프로젝트 개요

주식회사 초록의 고객 응대 챗봇입니다.

# 애플리케이션 실행

## 실행 옵션


애플리케이션 실행 시 VectorStore 임베딩을 수행하려면 인자로 --mode=embedding 옵션을 주어야합니다.

임베딩 수행하지 않을 경우 옵션을 제외합니다.

```bash
# bootrun 실행 시
./gradlew bootrun
./gradlew bootrun --args='--mode=embedding'

# jar 실행 시
java -jar <jar 파일>
java -jar <jar 파일> --mode=embedding
```
