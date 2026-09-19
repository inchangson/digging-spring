# filter-interceptor-ex-code
- filter, interceptor 활용 예제 코드
- 상품 관리 서비스(회원 관리, 상품 관리 등 기능)를 기반으로 합니다.

## 참고 방식
- 각 브랜치가 위치한 커밋별로 확인할 수 있는 버젼이 다릅니다.
- feat/filter: filter로만 부가기능 구현
  - 부가기능: 로깅, 로그인 처리, csrf 방지(referrer tag 확인을 통한)
- feat/interceptor: interceptor로만 부가기능 구현
  - 부가기능: 로깅, 로그인 처리, api 실행 시간 측정
- compare_same_func: 동일한 기능에 대해 filter, interceptor 구현 시 어떤 차이가 있는지 비교할 수 있는 버젼
- 최종 master 브랜치는 filter, interceptor의 특징에 맞게 기능을 간추린 최종 버젼입니다.
