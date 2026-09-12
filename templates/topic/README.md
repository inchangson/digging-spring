# Topic Name

이 주제의 학습 목적과 확인할 질문을 적습니다.

- 예제: `src/main/java`
- 개념 검증: `src/test/java`
- 상세 실험 기록: `docs/` (필요할 때 추가)
- Java 기본 버전: 17

루트 `settings.gradle`에 `include 'topics:topic-name'`을 추가한 뒤,
저장소 루트에서 `./gradlew :topics:topic-name:test`로 실행합니다.
공통 테스트 의존성은 루트에서 제공하며 필요한 Spring 의존성은 이 모듈에서 추가합니다.
Boot 앱으로 실행하려면 `build.gradle`의 Boot 플러그인을 활성화하고
`@SpringBootApplication` main 클래스를 작성한 뒤 `:topics:topic-name:bootRun`을 사용합니다.
