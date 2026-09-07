package example.sas;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class ProtocolAndTokenPolicyTest extends OAuthTestSupport {
    @AfterEach void restore() {
        jdbc.update("update demo_policy set access_ttl_seconds=300,refresh_enabled=true where client_id='alpha-web'");
    }
    @Test void metadataAndJwkEndpointsProvided() throws Exception {
        var metadata = mvc.perform(get("/.well-known/oauth-authorization-server")).andReturn().getResponse();
        assertThat(metadata.getStatus()).isEqualTo(200);
        assertThat(metadata.getContentAsString()).contains("authorization_endpoint","token_endpoint","revocation_endpoint","jwks_uri");
        assertThat(mvc.perform(get("/oauth2/jwks")).andReturn().getResponse().getContentAsString()).contains("RSA");
    }
    @Test void openidProducesIdToken() throws Exception {
        String code = code(authorize("alpha-web",CALLBACK,"openid profile",null,null));
        assertThat(json.readTree(token("alpha-web",code,null,CALLBACK).getResponse().getContentAsString()).has("id_token")).isTrue();
    }
    @Test void claimsAreBoundToPolicyAndGrantedScope() throws Exception {
        String code = code(authorize("alpha-web",CALLBACK,"email",null,null));
        var body = json.readTree(token("alpha-web",code,null,CALLBACK).getResponse().getContentAsString());
        var claims = json.readTree(new String(Base64.getUrlDecoder().decode(body.get("access_token").asText().split("\\.")[1]),StandardCharsets.UTF_8));
        assertThat(claims.get("tenant").asText()).isEqualTo("alpha");
        assertThat(claims.get("service").asText()).isEqualTo("web");
        assertThat(claims.has("profile_name")).isFalse();
    }
    @Test void accessTtlChangesWithoutRestart() throws Exception {
        jdbc.update("update demo_policy set access_ttl_seconds=47 where client_id='alpha-web'");
        String code = code(authorize("alpha-web",CALLBACK,"profile",null,null));
        assertThat(json.readTree(token("alpha-web",code,null,CALLBACK).getResponse().getContentAsString()).get("expires_in").asInt()).isBetween(45,47);
    }
    @Test void refreshCanBeDisabledPerClientWithoutRestart() throws Exception {
        jdbc.update("update demo_policy set refresh_enabled=false where client_id='alpha-web'");
        String code = code(authorize("alpha-web",CALLBACK,"profile",null,null));
        assertThat(json.readTree(token("alpha-web",code,null,CALLBACK).getResponse().getContentAsString()).has("refresh_token")).isFalse();
    }
}
