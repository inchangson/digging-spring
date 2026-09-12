package example.sas;

import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Serializes consumption of one grant across instances. The transaction-scoped advisory lock and
 * SAS JDBC operations share Spring's thread-bound connection, avoiding pool starvation caused by
 * holding a separate session lock while the token provider asks for another connection.
 */
@Component
public class TokenGrantLockFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final boolean enabled;
    public TokenGrantLockFilter(JdbcTemplate jdbc, PlatformTransactionManager manager,
        @Value("${demo.token-lock-enabled:true}") boolean enabled) {
        this.jdbc=jdbc; this.transaction=new TransactionTemplate(manager); this.enabled=enabled;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !request.getMethod().equals("POST") || !request.getRequestURI().equals("/oauth2/token")
            || !("authorization_code".equals(request.getParameter("grant_type")) || "refresh_token".equals(request.getParameter("grant_type")));
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
        throws ServletException,IOException {
        String grant = "authorization_code".equals(request.getParameter("grant_type")) ? request.getParameter("code") : request.getParameter("refresh_token");
        if (grant == null || grant.isBlank()) { chain.doFilter(request,response); return; }
        final Exception[] failure = new Exception[1];
        transaction.executeWithoutResult(status -> {
            jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?, 0))", grant);
            try { chain.doFilter(request,response); } catch (IOException | ServletException e) { status.setRollbackOnly(); failure[0]=e; }
        });
        if (failure[0] instanceof IOException e) throw e;
        if (failure[0] instanceof ServletException e) throw e;
    }
}
