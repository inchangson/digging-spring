package example.sas;

import java.net.URI;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.*;
import org.springframework.web.util.UriComponentsBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest @AutoConfigureMockMvc
abstract class OAuthTestSupport {
    static final String CALLBACK = "https://client.example/callback";
    static final String VERIFIER = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    static String challenge() throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(VERIFIER.getBytes(StandardCharsets.US_ASCII)));
    }
    MvcResult authorize(String client, String redirect, String scope, String method, String challenge) throws Exception {
        var request = get("/oauth2/authorize").with(user("alpha:user")).queryParam("response_type", "code")
            .queryParam("client_id", client).queryParam("redirect_uri", redirect).queryParam("scope", scope).queryParam("state", "state123");
        if (method != null) request.queryParam("code_challenge_method", method);
        if (challenge != null) request.queryParam("code_challenge", challenge);
        return mvc.perform(request).andReturn();
    }
    String code(MvcResult result) {
        String location = result.getResponse().getRedirectedUrl();
        if (location == null) throw new AssertionError("No redirect: " + result.getResponse().getStatus());
        String code = UriComponentsBuilder.fromUri(URI.create(location)).build().getQueryParams().getFirst("code");
        if (code == null) throw new AssertionError("No code: " + location);
        return code;
    }
    MvcResult token(String client, String code, String verifier, String redirect) throws Exception {
        var request = post("/oauth2/token").contentType("application/x-www-form-urlencoded")
            .param("grant_type", "authorization_code").param("code", code).param("redirect_uri", redirect);
        if (client.endsWith("mobile")) request.param("client_id", client);
        else request.with(httpBasic(client, client + "-secret"));
        if (verifier != null) request.param("code_verifier", verifier);
        return mvc.perform(request).andReturn();
    }
}
