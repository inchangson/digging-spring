package example.sas;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class BaselineTest extends OAuthTestSupport {
    @Test void confidentialCodeExchangeUsesRealSasAndPostgres() throws Exception {
        var auth = authorize("alpha-web", CALLBACK, "profile", null, null);
        assertThat(auth.getResponse().getRedirectedUrl()).contains("state=state123");
        var response = token("alpha-web", code(auth), null, CALLBACK).getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(json.readTree(response.getContentAsString()).get("access_token").asText()).isNotBlank();
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        assertThat(jdbc.queryForObject("select count(*) from oauth2_authorization", Long.class)).isPositive();
    }
    @Test void publicS256SucceedsWithoutRefreshToken() throws Exception {
        String code = code(authorize("alpha-mobile", CALLBACK, "profile", "S256", challenge()));
        var response = token("alpha-mobile", code, VERIFIER, CALLBACK).getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(json.readTree(response.getContentAsString()).has("refresh_token")).isFalse();
    }
    @Test void anonymousRequestEntersLogin() throws Exception {
        var response = mvc.perform(get("/oauth2/authorize").queryParam("response_type", "code")
            .queryParam("client_id", "alpha-web").queryParam("redirect_uri", CALLBACK).queryParam("scope", "profile")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).contains("/login");
    }
    @Test void invalidRedirectIsNotFollowed() throws Exception {
        var response = authorize("alpha-web", "https://unregistered.example/cb", "profile", null, null).getResponse();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getRedirectedUrl()).isNull();
    }
    @Test void unsupportedScopeRejected() throws Exception {
        assertThat(authorize("alpha-web", CALLBACK, "administrator", null, null).getResponse().getRedirectedUrl()).contains("error=invalid_scope");
    }
}
