package example.sas;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import static org.assertj.core.api.Assertions.*;

class PkceBoundaryTest extends OAuthTestSupport {
    @Autowired JdbcRegisteredClientRepository clients;
    @AfterEach void restore() { clients.save(SasConfiguration.client("alpha-mobile", true)); }
    @Test void requiredChallengeMissingRejected() throws Exception {
        assertThat(authorize("alpha-mobile", CALLBACK, "profile", null, null).getResponse().getRedirectedUrl()).contains("error=invalid_request");
    }
    @Test void plainNotSupportedByDefault() throws Exception {
        assertThat(authorize("alpha-mobile", CALLBACK, "profile", "plain", VERIFIER).getResponse().getRedirectedUrl()).contains("error=invalid_request");
    }
    @Test void optionalDoesNotMeanIgnoreSuppliedChallenge() throws Exception {
        String code = code(authorize("alpha-web", CALLBACK, "profile", "S256", challenge()));
        assertThat(token("alpha-web", code, "wrong", CALLBACK).getResponse().getStatus()).isEqualTo(400);
        assertThat(token("alpha-web", code, VERIFIER, CALLBACK).getResponse().getStatus()).isEqualTo(200);
    }
    @Test void publicRequireProofKeyFalseStillDoesNotAllowNoVerifier() throws Exception {
        clients.save(RegisteredClient.from(clients.findByClientId("alpha-mobile"))
            .clientSettings(ClientSettings.builder().requireProofKey(false).build()).build());
        String code = code(authorize("alpha-mobile", CALLBACK, "profile", null, null));
        assertThat(token("alpha-mobile", code, null, CALLBACK).getResponse().getStatus()).isBetween(400, 499);
    }
    @Test void confidentialVerifierWithoutChallengeRejected() throws Exception {
        String code = code(authorize("alpha-web", CALLBACK, "profile", null, null));
        assertThat(token("alpha-web", code, VERIFIER, CALLBACK).getResponse().getStatus()).isEqualTo(400);
    }
    @Test void publicWrongVerifierRejected() throws Exception {
        String code = code(authorize("alpha-mobile", CALLBACK, "profile", "S256", challenge()));
        assertThat(token("alpha-mobile", code, "wrong", CALLBACK).getResponse().getStatus()).isEqualTo(400);
    }
}
