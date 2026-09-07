package example.sas;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import static org.assertj.core.api.Assertions.*;

class ConcurrencyTest extends OAuthTestSupport {
    @RepeatedTest(5) void oneAuthorizationCodeHasOnlyOneConcurrentWinner() throws Exception {
        String code = code(authorize("alpha-web",CALLBACK,"profile",null,null));
        assertSingleWinner(concurrentStatuses(16, () -> token("alpha-web",code,null,CALLBACK).getResponse().getStatus()), "code exchange");
    }
    @RepeatedTest(5) void oneRefreshTokenHasOnlyOneConcurrentRotationWinner() throws Exception {
        String code = code(authorize("alpha-web",CALLBACK,"profile",null,null));
        String refresh = json.readTree(token("alpha-web",code,null,CALLBACK).getResponse().getContentAsString()).get("refresh_token").asText();
        assertSingleWinner(concurrentStatuses(16, () -> rotate(refresh)), "refresh rotation");
    }
    private int rotate(String refresh) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/oauth2/token")
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("alpha-web","alpha-web-secret"))
            .param("grant_type","refresh_token").param("refresh_token",refresh)).andReturn().getResponse().getStatus();
    }
    protected List<Integer> concurrentStatuses(int count, ThrowingIntSupplier action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count), start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i=0;i<count;i++) futures.add(pool.submit(() -> { ready.countDown(); start.await(); return action.get(); }));
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue(); start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) statuses.add(future.get(15,TimeUnit.SECONDS));
            return statuses;
        } finally { pool.shutdownNow(); }
    }
    protected void assertSingleWinner(List<Integer> statuses, String operation) {
        assertThat(statuses).as("HTTP statuses for concurrent " + operation).allMatch(s -> s == 200 || s == 400);
        assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
    }
    @FunctionalInterface protected interface ThrowingIntSupplier { int get() throws Exception; }
}
