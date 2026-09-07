package example.sas;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.*;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

@Configuration
public class SasConfiguration {
    @Bean JdbcRegisteredClientRepository clients(JdbcTemplate jdbc) throws Exception {
        for (String file : new String[]{"client/oauth2-registered-client-schema.sql", "oauth2-authorization-schema.sql", "oauth2-authorization-consent-schema.sql"}) {
            String sql = new ClassPathResource("org/springframework/security/oauth2/server/authorization/" + file)
                .getContentAsString(StandardCharsets.UTF_8)
                .replace("CREATE TABLE ", "CREATE TABLE IF NOT EXISTS ")
                .replace("blob", "text").replace("timestamp", "timestamptz");
            jdbc.execute(sql);
        }
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS pg_stat_statements");
        JdbcRegisteredClientRepository repo = new JdbcRegisteredClientRepository(jdbc);
        for (String id : new String[]{"alpha-web", "alpha-mobile", "beta-web", "poc-web", "load-client"}) {
            repo.save(client(id, id.endsWith("mobile")));
        }
        return repo;
    }
    static RegisteredClient client(String id, boolean publicClient) {
        var builder = RegisteredClient.withId(id).clientId(id).clientSecret(publicClient ? null : "{noop}" + id + "-secret")
            .clientAuthenticationMethod(publicClient ? ClientAuthenticationMethod.NONE : ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("https://client.example/callback").scope("openid").scope("profile").scope("email")
            .clientSettings(ClientSettings.builder().requireProofKey(publicClient).requireAuthorizationConsent(false).build())
            .tokenSettings(TokenSettings.builder().accessTokenTimeToLive(Duration.ofMinutes(5))
                .refreshTokenTimeToLive(Duration.ofMinutes(30)).reuseRefreshTokens(false).build());
        if (id.equals("load-client")) builder.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS);
        return builder.build();
    }
    @Bean OAuth2AuthorizationService authorizations(JdbcTemplate jdbc, JdbcRegisteredClientRepository clients,
        Policies policies) {
        return new PolicySnapshotAuthorizationService(new JdbcOAuth2AuthorizationService(jdbc, clients), clients, policies);
    }
    @Bean OAuth2AuthorizationConsentService consents(JdbcTemplate jdbc, JdbcRegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationConsentService(jdbc, clients);
    }
    @Bean @Order(1) SecurityFilterChain authorizationChain(HttpSecurity http, PolicyValidator validator, Policies policies,
        RegisteredClientRepository effectiveClients, OAuth2AuthorizationService authorizations, LoginExperience login,
        TokenGrantLockFilter tokenGrantLock) throws Exception {
        var sas = OAuth2AuthorizationServerConfigurer.authorizationServer();
        http.securityMatcher(sas.getEndpointsMatcher())
            .with(sas, config -> config.registeredClientRepository(effectiveClients).oidc(Customizer.withDefaults())
                .clientAuthentication(client -> client.authenticationConverter(PkceCompatibility.publicOffConverter(policies))
                    .authenticationProvider(PkceCompatibility.publicOffProvider(policies, effectiveClients, authorizations)))
                .authorizationEndpoint(endpoint -> endpoint
                    .authorizationRequestConverters(converters -> {
                        converters.removeIf(c -> c instanceof org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2AuthorizationCodeRequestAuthenticationConverter);
                        converters.add(0, PkceCompatibility.authorizationConverter(policies));
                    })
                    .authenticationProviders(providers ->
                providers.forEach(provider -> {
                    if (provider instanceof org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider p)
                        p.setAuthenticationValidator(validator);
                }))))
            .authorizeHttpRequests(a -> a.anyRequest().authenticated())
            .exceptionHandling(e -> e.authenticationEntryPoint(login.entryPoint()))
            .addFilterAfter(tokenGrantLock,
                org.springframework.security.web.context.SecurityContextHolderFilter.class);
        return http.build();
    }
    @Bean @Order(2) SecurityFilterChain loginChain(HttpSecurity http, LoginExperience login) throws Exception {
        http.authorizeHttpRequests(a -> a.requestMatchers("/assets/**", "/login").permitAll().anyRequest().authenticated())
            .formLogin(form -> form.loginPage("/login").successHandler(login::success).permitAll())
            .addFilterBefore(login.verificationFilter(), org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    @Bean UserDetailsService users() {
        return new InMemoryUserDetailsManager(User.withUsername("alpha:user").password("{noop}password").roles("USER").build(),
            User.withUsername("beta:user").password("{noop}password").roles("USER").build());
    }
    @Bean JWKSource<SecurityContext> keys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var key = new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey((RSAPrivateKey) pair.getPrivate())
            .keyID(UUID.randomUUID().toString()).build();
        return (selector, context) -> selector.select(new JWKSet(key));
    }
    @Bean AuthorizationServerSettings settings() {
        return AuthorizationServerSettings.builder().issuer("http://localhost:9099").build();
    }
    @Bean OAuth2TokenCustomizer<JwtEncodingContext> tenantClaims(Policies policies) {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) return;
            Map<String,Object> snapshot = context.getAuthorization() == null ? null
                : context.getAuthorization().getAttribute(PolicySnapshotAuthorizationService.ATTRIBUTE);
            if (snapshot == null) {
                var policy = policies.get(context.getRegisteredClient().getClientId());
                snapshot = Map.of("tenant", policy.tenant(), "service", policy.service());
            }
            context.getClaims().claim("tenant", snapshot.get("tenant")).claim("service", snapshot.get("service"));
            if (context.getAuthorizedScopes().contains("profile")) context.getClaims().claim("profile_name", "Demo User");
        };
    }
}
