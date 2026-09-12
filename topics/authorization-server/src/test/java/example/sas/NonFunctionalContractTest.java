package example.sas;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class NonFunctionalContractTest extends OAuthTestSupport {
    @Test void tokenResponseDisablesCachingAndIncludesBaselineBrowserProtections() throws Exception {
        var response = mvc.perform(post("/oauth2/token")
            .with(httpBasic("load-client", "load-client-secret"))
            .contentType("application/x-www-form-urlencoded")
            .param("grant_type", "client_credentials")
            .param("scope", "profile")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        assertThat(response.getHeader(HttpHeaders.PRAGMA)).isEqualTo("no-cache");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
    }

    @Test void oauthErrorIsBoundedAndDoesNotLeakImplementationDetails() throws Exception {
        var response = mvc.perform(post("/oauth2/token")
            .with(httpBasic("load-client", "wrong-secret"))
            .contentType("application/x-www-form-urlencoded")
            .param("grant_type", "client_credentials"))
            .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsByteArray()).hasSizeLessThan(512);
        assertThat(response.getContentAsString()).contains("invalid_client")
            .doesNotContain("Exception", "java.", "org.springframework", "select ", "insert ", "update ");
    }
}
