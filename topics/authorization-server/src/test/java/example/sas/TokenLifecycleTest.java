package example.sas;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

class TokenLifecycleTest extends OAuthTestSupport {
    String refresh() throws Exception {
        String code = code(authorize("alpha-web",CALLBACK,"profile",null,null));
        return json.readTree(token("alpha-web",code,null,CALLBACK).getResponse().getContentAsString()).get("refresh_token").asText();
    }
    org.springframework.mock.web.MockHttpServletResponse rotate(String client,String refresh) throws Exception {
        return mvc.perform(post("/oauth2/token").with(httpBasic(client,client+"-secret"))
            .param("grant_type","refresh_token").param("refresh_token",refresh)).andReturn().getResponse();
    }
    @Test void refreshRotationRejectsOldToken() throws Exception {
        String old = refresh(); var response = rotate("alpha-web",old);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(json.readTree(response.getContentAsString()).get("refresh_token").asText()).isNotEqualTo(old);
        assertThat(rotate("alpha-web",old).getStatus()).isEqualTo(400);
    }
    @Test void refreshBoundToClient() throws Exception { assertThat(rotate("beta-web",refresh()).getStatus()).isEqualTo(400); }
    @Test void expiredCodeAndRefreshDenied() throws Exception {
        String code = code(authorize("alpha-web",CALLBACK,"profile",null,null));
        jdbc.update("update oauth2_authorization set authorization_code_issued_at=now()-interval '2 minutes', authorization_code_expires_at=now()-interval '1 second' where authorization_code_value=?",code);
        assertThat(token("alpha-web",code,null,CALLBACK).getResponse().getStatus()).isEqualTo(400);
        String refresh = refresh();
        jdbc.update("update oauth2_authorization set refresh_token_issued_at=now()-interval '2 minutes', refresh_token_expires_at=now()-interval '1 second' where refresh_token_value=?",refresh);
        assertThat(rotate("alpha-web",refresh).getStatus()).isEqualTo(400);
    }
    @Test void revocationIsIdempotentAndMakesRefreshUnusable() throws Exception {
        String refresh = refresh();
        for (String value : new String[]{refresh,refresh,"not-a-real-token"}) {
            assertThat(mvc.perform(post("/oauth2/revoke").with(httpBasic("alpha-web","alpha-web-secret")).param("token",value))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        }
        assertThat(rotate("alpha-web",refresh).getStatus()).isEqualTo(400);
    }
    @Test void wrongSecretAndWrongCodeClientDenied() throws Exception {
        String code = code(authorize("alpha-web",CALLBACK,"profile",null,null));
        assertThat(mvc.perform(post("/oauth2/token").with(httpBasic("alpha-web","wrong"))
            .param("grant_type","authorization_code").param("code",code).param("redirect_uri",CALLBACK)).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(token("beta-web",code,null,CALLBACK).getResponse().getStatus()).isEqualTo(400);
    }
    @Test void reuseDenied() throws Exception {
        String code = code(authorize("alpha-web",CALLBACK,"profile",null,null));
        assertThat(token("alpha-web",code,null,CALLBACK).getResponse().getStatus()).isEqualTo(200);
        assertThat(token("alpha-web",code,null,CALLBACK).getResponse().getStatus()).isEqualTo(400);
    }
    @Test void unsupportedGrantAndDuplicateParameterRejected() throws Exception {
        assertThat(mvc.perform(post("/oauth2/token").with(httpBasic("alpha-web","alpha-web-secret"))
            .param("grant_type","password")).andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(mvc.perform(get("/oauth2/authorize").queryParam("response_type","code","code")
            .queryParam("client_id","alpha-web")).andReturn().getResponse().getStatus()).isEqualTo(400);
    }
}
