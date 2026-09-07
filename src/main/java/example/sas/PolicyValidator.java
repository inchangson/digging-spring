package example.sas;

import java.net.URI;
import java.util.function.Consumer;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.authentication.*;
import org.springframework.stereotype.Component;

@Component
public class PolicyValidator implements Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> {
    private final Policies policies;
    public PolicyValidator(Policies policies) { this.policies = policies; }
    @Override public void accept(OAuth2AuthorizationCodeRequestAuthenticationContext context) {
        OAuth2AuthorizationCodeRequestAuthenticationToken request = context.getAuthentication();
        var policy = policies.get(context.getRegisteredClient().getClientId());
        if (!policy.enabled()) reject("unauthorized_client");
        Authentication principal = (Authentication) request.getPrincipal();
        if (principal != null && principal.isAuthenticated() && !(principal instanceof AnonymousAuthenticationToken)
            && !principal.getName().startsWith(policy.tenant() + ":")) reject("access_denied");
        switch (policy.redirectMode()) {
            case "strict" -> OAuth2AuthorizationCodeRequestAuthenticationValidator.DEFAULT_REDIRECT_URI_VALIDATOR.accept(context);
            case "bypass" -> {
                // Demonstration of compatibility only: production must not allow arbitrary callback targets.
                URI uri;
                try { uri = URI.create(request.getRedirectUri()); } catch (Exception e) { reject("invalid_request"); return; }
                if (!uri.isAbsolute() || uri.getFragment() != null) reject("invalid_request");
            }
            default -> reject("server_error");
        }
        switch (policy.scopeMode()) {
            case "strict" -> OAuth2AuthorizationCodeRequestAuthenticationValidator.DEFAULT_SCOPE_VALIDATOR.accept(context);
            case "bypass" -> { }
            default -> reject("server_error");
        }
    }
    static void reject(String error) {
        throw new OAuth2AuthorizationCodeRequestAuthenticationException(new OAuth2Error(error), null);
    }
}
