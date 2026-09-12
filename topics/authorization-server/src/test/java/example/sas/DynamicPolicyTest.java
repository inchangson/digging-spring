package example.sas;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;

class DynamicPolicyTest extends OAuthTestSupport {
    @Autowired Policies policies;
    @AfterEach void restore() {
        jdbc.update("update demo_policy set redirect_mode='strict', scope_mode='strict', enabled=true where client_id='poc-web'");
        policies.evict("poc-web");
    }
    @Test void redirectBypassChangesWithoutRestartAndDoesNotAffectOtherClients() throws Exception {
        String other = "https://poc.example/callback";
        assertThat(authorize("poc-web", other, "profile", null, null).getResponse().getStatus()).isEqualTo(400);
        jdbc.update("update demo_policy set redirect_mode='bypass' where client_id='poc-web'");
        String code = code(authorize("poc-web", other, "profile", null, null));
        assertThat(token("poc-web", code, null, other).getResponse().getStatus()).isEqualTo(200);
        assertThat(authorize("alpha-web", other, "profile", null, null).getResponse().getStatus()).isEqualTo(400);
    }
    @Test void bypassDoesNotDisableTokenRedirectBinding() throws Exception {
        jdbc.update("update demo_policy set redirect_mode='bypass' where client_id='poc-web'");
        String code = code(authorize("poc-web", "https://poc.example/callback", "profile", null, null));
        assertThat(token("poc-web", code, null, CALLBACK).getResponse().getStatus()).isEqualTo(400);
    }
    @Test void bypassStillRejectsRelativeUriAndFragment() throws Exception {
        jdbc.update("update demo_policy set redirect_mode='bypass' where client_id='poc-web'");
        assertThat(authorize("poc-web", "/cb", "profile", null, null).getResponse().getStatus()).isEqualTo(400);
        assertThat(authorize("poc-web", CALLBACK + "#fragment", "profile", null, null).getResponse().getStatus()).isEqualTo(400);
    }
    @Test void scopeBypassReachesIssuedToken() throws Exception {
        jdbc.update("update demo_policy set scope_mode='bypass' where client_id='poc-web'");
        String code = code(authorize("poc-web", CALLBACK, "custom", null, null));
        var response = token("poc-web", code, null, CALLBACK).getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(json.readTree(response.getContentAsString()).get("scope").asText()).isEqualTo("custom");
    }
    @Test void unknownStepFailsClosed() throws Exception {
        jdbc.update("update demo_policy set redirect_mode='typo' where client_id='poc-web'");
        assertThat(authorize("poc-web", CALLBACK, "profile", null, null).getResponse().getStatus()).isBetween(400, 599);
    }
    @Test void tenantMismatchRejectedEvenWithExistingLogin() throws Exception {
        assertThat(authorize("beta-web", CALLBACK, "profile", null, null).getResponse().getStatus()).isEqualTo(400);
    }
    @Test void disabledClientDenied() throws Exception {
        jdbc.update("update demo_policy set enabled=false where client_id='poc-web'");
        assertThat(authorize("poc-web", CALLBACK, "profile", null, null).getResponse().getStatus()).isEqualTo(400);
    }
}
