package example.sas;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PolicySnapshotTest extends OAuthTestSupport {
    @AfterEach void restore() {
        jdbc.update("update demo_policy set tenant='alpha',service='web',enabled=true where client_id='alpha-web'");
    }

    @Test void authorizationKeepsOriginalTenantAndServiceWhenLivePolicyChangesBeforeTokenExchange() throws Exception {
        String code = code(authorize("alpha-web", CALLBACK, "profile", null, null));
        jdbc.update("update demo_policy set tenant='changed',service='changed',version=version+1 where client_id='alpha-web'");

        var response = token("alpha-web", code, null, CALLBACK).getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        var body = json.readTree(response.getContentAsString());
        var claims = json.readTree(new String(Base64.getUrlDecoder().decode(
            body.get("access_token").asText().split("\\.")[1]), StandardCharsets.UTF_8));
        assertThat(claims.get("tenant").asText()).isEqualTo("alpha");
        assertThat(claims.get("service").asText()).isEqualTo("web");
    }

    @Test void disablingClientAfterAuthorizationBlocksTokenExchange() throws Exception {
        String code = code(authorize("alpha-web", CALLBACK, "profile", null, null));
        jdbc.update("update demo_policy set enabled=false,version=version+1 where client_id='alpha-web'");

        assertThat(token("alpha-web", code, null, CALLBACK).getResponse().getStatus()).isEqualTo(401);
    }
}
