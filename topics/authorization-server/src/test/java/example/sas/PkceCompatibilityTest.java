package example.sas;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class PkceCompatibilityTest extends OAuthTestSupport {
    @AfterEach void restore() { jdbc.update("update demo_policy set pkce_mode='standard' where client_id='alpha-mobile'"); }
    @Test void offPublicClientExchangesWithoutVerifier() throws Exception {
        jdbc.update("update demo_policy set pkce_mode='off' where client_id='alpha-mobile'");
        String code = code(authorize("alpha-mobile", CALLBACK, "profile", null, null));
        assertThat(token("alpha-mobile", code, null, CALLBACK).getResponse().getStatus()).isEqualTo(200);
    }
    @Test void offIgnoresSuppliedChallengeAndVerifier() throws Exception {
        jdbc.update("update demo_policy set pkce_mode='off' where client_id='alpha-mobile'");
        String code = code(authorize("alpha-mobile", CALLBACK, "profile", "plain", "ignored"));
        assertThat(token("alpha-mobile", code, "also-ignored", CALLBACK).getResponse().getStatus()).isEqualTo(200);
    }
    @Test void plainCanBeNormalizedBeforeDefaultSasProvider() throws Exception {
        jdbc.update("update demo_policy set pkce_mode='plain' where client_id='alpha-mobile'");
        String code = code(authorize("alpha-mobile", CALLBACK, "profile", "plain", VERIFIER));
        assertThat(token("alpha-mobile", code, "wrong", CALLBACK).getResponse().getStatus()).isEqualTo(400);
        assertThat(token("alpha-mobile", code, VERIFIER, CALLBACK).getResponse().getStatus()).isEqualTo(200);
    }
    @Test void livePolicyChangeCannotDowngradeExistingS256Grant() throws Exception {
        String code = code(authorize("alpha-mobile", CALLBACK, "profile", "S256", challenge()));
        jdbc.update("update demo_policy set pkce_mode='off' where client_id='alpha-mobile'");
        assertThat(token("alpha-mobile", code, null, CALLBACK).getResponse().getStatus()).isEqualTo(400);
    }
    @Test void offDoesNotBypassCodeReuseOrRedirectBinding() throws Exception {
        jdbc.update("update demo_policy set pkce_mode='off' where client_id='alpha-mobile'");
        String code = code(authorize("alpha-mobile", CALLBACK, "profile", null, null));
        assertThat(token("alpha-mobile", code, null, "https://wrong.example/cb").getResponse().getStatus()).isEqualTo(400);
        assertThat(token("alpha-mobile", code, null, CALLBACK).getResponse().getStatus()).isEqualTo(200);
        assertThat(token("alpha-mobile", code, null, CALLBACK).getResponse().getStatus()).isEqualTo(400);
    }
}
