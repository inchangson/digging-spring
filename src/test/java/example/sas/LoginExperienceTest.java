package example.sas;

import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

class LoginExperienceTest extends OAuthTestSupport {
    @AfterEach void restore() { jdbc.update("update demo_policy set theme='standard',icon='/assets/alpha.svg',otp=false,captcha=false"); }
    MvcResult start(String client, MockHttpSession session) throws Exception {
        return mvc.perform(get("/oauth2/authorize").session(session).queryParam("client_id",client)
            .queryParam("response_type","code").queryParam("redirect_uri",CALLBACK).queryParam("scope","profile")).andReturn();
    }
    String flow(MvcResult result) { return result.getResponse().getRedirectedUrl().split("flow=")[1]; }
    @Test void brandingChangesLiveAndTabsStayIndependent() throws Exception {
        var session = new MockHttpSession();
        String alpha = flow(start("alpha-web",session)); String beta = flow(start("beta-web",session));
        jdbc.update("update demo_policy set theme='compact',icon='/assets/beta.svg' where client_id='beta-web'");
        var a = mvc.perform(get("/login").session(session).queryParam("flow",alpha)).andReturn().getResponse();
        var b = mvc.perform(get("/login").session(session).queryParam("flow",beta)).andReturn().getResponse();
        assertThat(a.getContentAsString()).contains("/assets/alpha.svg").doesNotContain("Compact experience");
        assertThat(b.getContentAsString()).contains("/assets/beta.svg", "Compact experience");
    }
    @Test void csrfAndTenantAndPasswordRemainRequired() throws Exception {
        var session = new MockHttpSession(); String id = flow(start("alpha-web",session));
        assertThat(mvc.perform(post("/login").session(session).param("flow",id).param("username","alpha:user").param("password","password"))
            .andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(post("/login").session(session).with(csrf()).param("flow",id).param("username","beta:user").param("password","password"))
            .andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(post("/login").session(session).with(csrf()).param("flow",id).param("username","alpha:user").param("password","bad"))
            .andReturn().getResponse().getRedirectedUrl()).contains("error");
    }
    @Test void otpCaptchaRequiredThenPocBypassAndActualPasswordLoginResumesSas() throws Exception {
        jdbc.update("update demo_policy set otp=true,captcha=true where client_id='poc-web'");
        var session = new MockHttpSession(); String id = flow(start("poc-web",session));
        var req = post("/login").session(session).with(csrf()).param("flow",id).param("username","alpha:user").param("password","password");
        assertThat(mvc.perform(req).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(post("/login").session(session).with(csrf()).param("flow",id).param("username","alpha:user")
            .param("password","password").param("otp","123456").param("captcha","wrong")).andReturn().getResponse().getStatus()).isEqualTo(401);
        jdbc.update("update demo_policy set otp=false,captcha=false where client_id='poc-web'");
        var login = mvc.perform(req).andReturn();
        assertThat(login.getResponse().getStatus()).isEqualTo(302);
        var auth = mvc.perform(get(login.getResponse().getRedirectedUrl()).session((MockHttpSession)login.getRequest().getSession())).andReturn();
        assertThat(token("poc-web",code(auth),null,CALLBACK).getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(get("/login").session(session).queryParam("flow",id)).andReturn().getResponse().getStatus()).isEqualTo(400);
    }
    @Test void fabricatedFlowAndUnregisteredTemplateDenied() throws Exception {
        assertThat(mvc.perform(get("/login").queryParam("flow","forged")).andReturn().getResponse().getStatus()).isEqualTo(400);
        var session = new MockHttpSession(); String id = flow(start("alpha-web",session));
        jdbc.update("update demo_policy set theme='../escape' where client_id='alpha-web'");
        assertThat(mvc.perform(get("/login").session(session).queryParam("flow",id)).andReturn().getResponse().getStatus()).isEqualTo(400);
    }
}
