package example.sas;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class Policies {
    public record Policy(String client, String tenant, String service, String redirectMode, String scopeMode,
        String pkceMode, String theme, String icon, boolean otp, boolean captcha, boolean enabled,
        int accessTtlSeconds, boolean refreshEnabled, long version) {}
    private record Cached(Policy value, long deadline) {}
    private final JdbcTemplate jdbc;
    private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();
    private final boolean cached;
    public Policies(JdbcTemplate jdbc, @Value("${demo.policy-cache:false}") boolean cached) {
        this.jdbc = jdbc; this.cached = cached;
        jdbc.execute("""
            create table if not exists demo_policy(
            client_id text primary key, tenant text not null, service text not null,
            redirect_mode text not null default 'strict', scope_mode text not null default 'strict',
            pkce_mode text not null default 'standard', theme text not null default 'standard',
            icon text not null default '/assets/alpha.svg', otp boolean not null default false,
            captcha boolean not null default false, enabled boolean not null default true, version bigint not null default 1)
            """);
        jdbc.execute("alter table demo_policy add column if not exists access_ttl_seconds integer not null default 300");
        jdbc.execute("alter table demo_policy add column if not exists refresh_enabled boolean not null default true");
        for (String id : new String[]{"alpha-web", "alpha-mobile", "beta-web", "poc-web", "load-client"}) {
            jdbc.update("insert into demo_policy(client_id,tenant,service) values (?,?,?) on conflict do nothing",
                id, id.startsWith("beta") ? "beta" : "alpha", id.endsWith("mobile") ? "mobile" : "web");
        }
    }
    public Policy get(String id) {
        if (id == null) throw new IllegalArgumentException("missing client");
        Cached entry = cache.get(id);
        if (cached && entry != null && entry.deadline > System.nanoTime()) return entry.value;
        Policy policy = jdbc.queryForObject("select * from demo_policy where client_id=?", (rs, row) -> new Policy(
            rs.getString("client_id"), rs.getString("tenant"), rs.getString("service"), rs.getString("redirect_mode"),
            rs.getString("scope_mode"), rs.getString("pkce_mode"), rs.getString("theme"), rs.getString("icon"),
            rs.getBoolean("otp"), rs.getBoolean("captcha"), rs.getBoolean("enabled"),
            rs.getInt("access_ttl_seconds"), rs.getBoolean("refresh_enabled"), rs.getLong("version")), id);
        if (cached) cache.put(id, new Cached(policy, System.nanoTime() + Duration.ofSeconds(2).toNanos()));
        return policy;
    }
    public void evict(String id) { cache.remove(id); }
}
