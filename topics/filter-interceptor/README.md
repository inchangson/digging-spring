# Filter / Interceptor

회원·상품 관리 서비스를 통해 Servlet Filter와 Spring MVC HandlerInterceptor의 역할과 실행 순서를 공부합니다.
메모리 저장소를 사용하므로 DB나 Docker가 필요 없으며, 재시작하면 데이터가 초기화됩니다.

## 실행

저장소 루트에서 JDK 17과 공통 Gradle Wrapper를 사용합니다.

```bash
./gradlew :topics:filter-interceptor:test
./gradlew :topics:filter-interceptor:bootRun
./gradlew :topics:filter-interceptor:bootJar
```

- 접속: <http://localhost:8080/space>
- 초기 계정: `test` / `test!`
- 초기 상품: `itemA`, `itemB`
- 실행 JAR: `topics/filter-interceptor/build/libs/filter-interceptor-1.0.0.jar`
- 테스트 보고서: `topics/filter-interceptor/build/reports/tests/test/index.html`

Authorization Server와 동시에 실행하려면 포트를 변경합니다.

```bash
./gradlew :topics:filter-interceptor:bootRun --args='--server.port=8081'
```

## 코드 읽는 순서

1. `web/config/FilterConfig`: Servlet 필터 등록과 URL 매핑
2. `web/filter/CsrfFilter`: GET 이외 요청의 Referer 검사와 `chain.doFilter()` 전후 로그
3. `web/config/InterceptorConfig`: 인터셉터 순서와 로그인 검사 제외 경로
4. `web/interceptor/`: 요청 로깅 → 로그인 확인 → 처리 시간 측정
5. `web/login/LoginController`: 세션 로그인과 로그아웃
6. `web/item/ItemController`: 상품 조회·등록·수정 및 입력 검증

정상 요청은 필터 → DispatcherServlet → Logging → Login → TimeMeasure → 컨트롤러 순으로 진입합니다.
인터셉터의 `postHandle`과 `afterCompletion`은 역순으로 실행되고 마지막으로 필터에 제어가 돌아옵니다.
필터가 차단하면 인터셉터까지 도달하지 않으며, 로그인 인터셉터가 차단하면 시간 측정과 컨트롤러를 실행하지 않습니다.
로그인 검사가 제외된 공개 경로에는 나머지 인터셉터가 각각의 등록 조건에 따라 적용됩니다.

## 확인할 동작

- 비로그인 상태로 `/space/items`에 접근하면 로그인 화면으로 이동합니다.
- 로그인하면 원래 요청한 상품 화면으로 돌아가고, 로그아웃하면 세션이 폐기됩니다.
- GET 이외 요청에 Referer가 없거나 `/space` 패턴과 맞지 않으면 필터가 400을 반환합니다.
- 상품 입력 오류는 폼에서 표시되며, 정상 입력은 메모리 저장소에 반영됩니다.
- `src/test/java/com/example/demo/WebFlowTests.java`에서 필터·인터셉터 등록을 포함한 요청 흐름을 검증합니다.

원본의 학습 동작을 유지했습니다. `CsrfFilter`의 Referer 정규식은 출처(origin)를 검증하지 않으므로
실제 CSRF 방어로 사용하기에는 부족합니다. 비밀번호 평문 저장과 사용자 입력 `redirectURL` 사용도
원본 예제에 남아 있는 보안 개선 과제입니다.

## 원본과 이식 범위

- 원본: [inchangson/filter-interceptor-ex-code](https://github.com/inchangson/filter-interceptor-ex-code)
- 기준 커밋: `6941cbdbc946a6c6c685f2f33c3ab4e61d7b6d78` (`master` 최종 예제)
- 원본 Java 패키지, 화면, 정적 리소스와 회원·상품 관리 흐름을 유지했습니다.
- Boot 2.7 / Java 8 설정을 저장소 공통 Boot 3.5 / Java 17 설정으로 통합했습니다.
- Servlet, Validation, PostConstruct를 `jakarta.*`로 전환하고 Spring 6의 HTTP 메서드 API에 맞췄습니다.
- 원본에서 누락된 `totalPriceMin` 검증 메시지를 추가해 합계 금액이 부족해도 오류 화면이 정상 렌더링되도록 했습니다.
- 원본의 개별 Wrapper와 중첩 프로젝트 설정 대신 루트 Wrapper와 모듈 설정을 사용합니다.

### Git 이력 보존

원본 `master`의 전체 이력을 squash 없이 subtree 병합으로 가져왔습니다.
원본 커밋의 해시·작성자·메시지를 유지하고, 이후 별도 커밋에서 `demo/src`를 모듈의 `src`로 옮기고
공통 빌드 설정과 Boot 3 호환 수정을 적용했습니다.
원본의 브랜치 끝점은 다음 보관용 태그로 남깁니다.

- `archive/filter-interceptor/master`
- `archive/filter-interceptor/feat-filter`
- `archive/filter-interceptor/feat-interceptor` (`master`에 병합되지 않은 마지막 커밋 포함)

```bash
git log --graph --oneline --all
git log archive/filter-interceptor/master
git show archive/filter-interceptor/feat-interceptor
# 통합 이후 파일 이동 이력
git log --follow -- topics/filter-interceptor/src/main/java/com/example/demo/web/filter/CsrfFilter.java
# subtree 병합 이전 이력은 원본 태그와 원본 경로로 조회
git log archive/filter-interceptor/master -- demo/src/main/java/com/example/demo/web/filter/CsrfFilter.java
```

`git log --follow`는 subtree 병합 경계에서 원본 경로까지 자동으로 추적하지 못할 수 있습니다.
원격에 보관용 태그도 남기려면 브랜치와 함께 위의 세 태그를 명시적으로 push해야 합니다.

구현 과정을 비교하는 원본 브랜치는 다음에서 확인할 수 있습니다. 이 모듈은 최종 예제를 실행합니다.

| 원본 브랜치 | 학습 내용 |
| --- | --- |
| [feat/filter](https://github.com/inchangson/filter-interceptor-ex-code/tree/feat/filter) | 필터로 로깅·로그인·Referer 검사 구현 |
| [feat/interceptor](https://github.com/inchangson/filter-interceptor-ex-code/tree/feat/interceptor) | 인터셉터로 로깅·로그인·실행 시간 측정 구현 |

원본 README에 언급된 `compare_same_func` 브랜치는 이식 시점의 원격 브랜치에 없어 링크에서 제외했습니다.
