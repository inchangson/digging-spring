package example.sas;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.authentication.*;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2AuthorizationCodeRequestAuthenticationConverter;
import org.springframework.security.web.authentication.AuthenticationConverter;

/** Local compatibility experiment. Standard clients always retain SAS's default processing. */
@Configuration
public class PkceCompatibility {
    @Bean @Primary RegisteredClientRepository effectiveClients(JdbcRegisteredClientRepository base, Policies policies) {
        return new RegisteredClientRepository() {
            public void save(RegisteredClient client) { base.save(client); }
            public RegisteredClient findById(String id) { return base.findById(id); }
            public RegisteredClient findByClientId(String id) {
                RegisteredClient client = base.findByClientId(id);
                if (client == null) return null;
                if (policies.get(id).pkceMode().equals("off")) return RegisteredClient.from(client)
                    .clientSettings(ClientSettings.withSettings(client.getClientSettings().getSettings()).requireProofKey(false).build()).build();
                return client;
            }
        };
    }
    static AuthenticationConverter authorizationConverter(Policies policies) {
        var delegate = new OAuth2AuthorizationCodeRequestAuthenticationConverter();
        return request -> {
            var converted = delegate.convert(request);
            if (!(converted instanceof OAuth2AuthorizationCodeRequestAuthenticationToken token)) return converted;
            Policies.Policy policy;
            try { policy = policies.get(token.getClientId()); } catch (org.springframework.dao.EmptyResultDataAccessException e) { return token; }
            Map<String,Object> extra = new HashMap<>(token.getAdditionalParameters());
            extra.put("demo.pkce_mode", policy.pkceMode());
            switch (policy.pkceMode()) {
                case "standard" -> { }
                case "off" -> { extra.remove("code_challenge"); extra.remove("code_challenge_method"); }
                case "plain" -> {
                    Object input = extra.get("code_challenge");
                    if (!(input instanceof String challenge) || !challenge.matches("[A-Za-z0-9._~-]{43,128}")
                        || !(extra.get("code_challenge_method") == null || "plain".equals(extra.get("code_challenge_method"))))
                        throw new OAuth2AuthenticationException("invalid_request");
                    try {
                        extra.put("code_challenge", Base64.getUrlEncoder().withoutPadding().encodeToString(
                            MessageDigest.getInstance("SHA-256").digest(challenge.getBytes(StandardCharsets.US_ASCII))));
                        extra.put("code_challenge_method", "S256");
                    } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
                }
                default -> throw new OAuth2AuthenticationException("server_error");
            }
            return new OAuth2AuthorizationCodeRequestAuthenticationToken(token.getAuthorizationUri(), token.getClientId(),
                (Authentication) token.getPrincipal(), token.getRedirectUri(), token.getState(), token.getScopes(), extra);
        };
    }
    static AuthenticationConverter publicOffConverter(Policies policies) {
        return request -> {
            if (!"authorization_code".equals(request.getParameter("grant_type")) || request.getHeader("Authorization") != null) return null;
            String client = request.getParameter("client_id");
            if (client == null) return null;
            Policies.Policy policy;
            try { policy = policies.get(client); } catch (org.springframework.dao.EmptyResultDataAccessException e) { return null; }
            if (!policy.pkceMode().equals("off")) return null;
            for (String field : List.of("client_id", "code", "grant_type")) {
                String[] values = request.getParameterValues(field);
                if (values == null || values.length != 1 || values[0].isBlank()) throw new OAuth2AuthenticationException("invalid_request");
            }
            return new OAuth2ClientAuthenticationToken(client, ClientAuthenticationMethod.NONE, null,
                Map.of("code", request.getParameter("code"), "demo.off", true));
        };
    }
    static AuthenticationProvider publicOffProvider(Policies policies, RegisteredClientRepository clients, OAuth2AuthorizationService authorizations) {
        return new AuthenticationProvider() {
            public boolean supports(Class<?> type) { return OAuth2ClientAuthenticationToken.class.isAssignableFrom(type); }
            public Authentication authenticate(Authentication authentication) {
                var token = (OAuth2ClientAuthenticationToken) authentication;
                if (!Boolean.TRUE.equals(token.getAdditionalParameters().get("demo.off"))) return null;
                var client = clients.findByClientId(token.getPrincipal().toString());
                if (client == null || !client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE)
                    || !policies.get(client.getClientId()).enabled()) throw new OAuth2AuthenticationException("invalid_client");
                var grant = authorizations.findByToken((String) token.getAdditionalParameters().get("code"), new OAuth2TokenType("code"));
                if (grant == null) throw new OAuth2AuthenticationException("invalid_grant");
                OAuth2AuthorizationRequest original = grant.getAttribute(OAuth2AuthorizationRequest.class.getName());
                // A live configuration change must not downgrade an already-issued S256 grant.
                if (!"off".equals(original.getAdditionalParameters().get("demo.pkce_mode"))) throw new OAuth2AuthenticationException("invalid_grant");
                return new OAuth2ClientAuthenticationToken(client, ClientAuthenticationMethod.NONE, null);
            }
        };
    }
}
