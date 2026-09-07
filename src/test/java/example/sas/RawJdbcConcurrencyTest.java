package example.sas;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties="demo.token-lock-enabled=false")
class RawJdbcConcurrencyTest extends OAuthTestSupport {
    @Test void rawSasJdbcServiceAllowsMultipleConcurrentCodeConsumers() throws Exception {
        List<Integer> winners = new ArrayList<>();
        for (int round=0;round<3;round++) {
            String code = code(authorize("alpha-web",CALLBACK,"profile",null,null));
            winners.add((int)concurrentStatuses(16, () -> token("alpha-web",code,null,CALLBACK).getResponse().getStatus())
                .stream().filter(s -> s == 200).count());
        }
        assertThat(winners).as("successful consumers per round without lock").anyMatch(count -> count > 1);
    }
    private List<Integer> concurrentStatuses(int count, ThrowingIntSupplier action) throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(count);
        CountDownLatch ready=new CountDownLatch(count),start=new CountDownLatch(1);
        try {
            List<Future<Integer>> futures=new ArrayList<>();
            for(int i=0;i<count;i++) futures.add(pool.submit(() -> { ready.countDown(); start.await(); return action.get(); }));
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue(); start.countDown();
            List<Integer> statuses=new ArrayList<>();
            for(Future<Integer> future:futures) statuses.add(future.get(15,TimeUnit.SECONDS));
            return statuses;
        } finally { pool.shutdownNow(); }
    }
    @FunctionalInterface interface ThrowingIntSupplier { int get() throws Exception; }
}
