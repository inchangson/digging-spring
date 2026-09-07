package example.sas;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/** Captures identity-binding policy when the authorization transaction starts. */
public final class PolicySnapshotAuthorizationService implements OAuth2AuthorizationService {
    public static final String ATTRIBUTE = "demo.policy_snapshot";
    private final OAuth2AuthorizationService delegate;
    private final RegisteredClientRepository clients;
    private final Policies policies;

    public PolicySnapshotAuthorizationService(OAuth2AuthorizationService delegate,
        RegisteredClientRepository clients, Policies policies) {
        this.delegate = delegate;
        this.clients = clients;
        this.policies = policies;
    }

    @Override public void save(OAuth2Authorization authorization) {
        if (authorization.getAttribute(ATTRIBUTE) == null) {
            var client = clients.findById(authorization.getRegisteredClientId());
            if (client != null) {
                var policy = policies.get(client.getClientId());
                Map<String, Object> snapshot = new LinkedHashMap<>();
                snapshot.put("tenant", policy.tenant());
                snapshot.put("service", policy.service());
                snapshot.put("policy_version", Long.toString(policy.version()));
                authorization = OAuth2Authorization.from(authorization)
                    .attribute(ATTRIBUTE, snapshot).build();
            }
        }
        delegate.save(authorization);
    }

    @Override public void remove(OAuth2Authorization authorization) { delegate.remove(authorization); }
    @Override public OAuth2Authorization findById(String id) { return delegate.findById(id); }
    @Override public OAuth2Authorization findByToken(String token, OAuth2TokenType type) {
        return delegate.findByToken(token, type);
    }
}
