package com.example.demo;

import com.example.demo.web.SessionConst;
import com.example.demo.web.interceptor.LoggingInterceptor;
import com.example.demo.web.interceptor.TimeMeasureInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WebFlowTests {
    @Autowired
    MockMvc mvc;

    @Test
    void publicPagesAndStaticResourcesAreAccessibleWithoutLogin() throws Exception {
        mvc.perform(get("/")).andExpect(redirectedUrl("/space"));
        for (String path : new String[]{"/space", "/space/info", "/space/login", "/space/members/add"}) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
        mvc.perform(get("/css/bootstrap.min.css")).andExpect(status().isOk());
    }

    @Test
    void loginInterceptorStopsProtectedRequestBeforeTimingInterceptor() throws Exception {
        mvc.perform(get("/space/items"))
                .andExpect(redirectedUrl("/space/login?redirectURL=/space/items"))
                .andExpect(request().attribute(LoggingInterceptor.LOG_ID, notNullValue()))
                .andExpect(request().attribute(TimeMeasureInterceptor.API_TIME, nullValue()));
    }

    @Test
    void filterRejectsMissingOrInvalidRefererBeforeInterceptors() throws Exception {
        mvc.perform(post("/space/login").param("loginId", "test").param("password", "test!"))
                .andExpect(status().isBadRequest())
                .andExpect(request().attribute(LoggingInterceptor.LOG_ID, nullValue()));
        mvc.perform(post("/space/login").header("Referer", "http://localhost/elsewhere"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidCredentialsAndValidationErrorsRenderForms() throws Exception {
        mvc.perform(post("/space/login").header("Referer", "http://localhost/space/login")
                        .param("loginId", "test").param("password", "wrong"))
                .andExpect(status().isOk()).andExpect(view().name("login/loginForm"))
                .andExpect(model().attributeHasErrors("loginForm"));
        mvc.perform(post("/space/members/add").header("Referer", "http://localhost/space/members/add"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("member", "loginId", "name", "password"));
        mvc.perform(post("/space/items/add").session(login()).header("Referer", "http://localhost/space/items/add")
                        .param("itemName", "").param("price", "500").param("quantity", "1"))
                .andExpect(status().isOk()).andExpect(view().name("items/addForm"))
                .andExpect(model().attributeHasFieldErrors("item", "itemName", "price"));
    }

    @Test
    void loggedInMemberCanCreateEditAndViewItemThenLogout() throws Exception {
        MockHttpSession session = login();
        mvc.perform(get("/space").session(session)).andExpect(view().name("loginHome"));
        mvc.perform(get("/space/items").session(session))
                .andExpect(status().isOk()).andExpect(content().string(containsString("itemA")))
                .andExpect(request().attribute(TimeMeasureInterceptor.API_TIME, notNullValue()));
        mvc.perform(get("/space/items/add").session(session)).andExpect(status().isOk());
        String location = mvc.perform(post("/space/items/add").session(session)
                        .header("Referer", "http://localhost/space/items/add")
                        .param("itemName", "new-item").param("price", "10000").param("quantity", "2"))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        assertThat(location).isNotNull();
        String itemPath = location.split("\\?")[0];
        String itemId = itemPath.substring(itemPath.lastIndexOf('/') + 1);
        mvc.perform(get(itemPath).session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("new-item")));
        mvc.perform(get(itemPath + "/edit").session(session)).andExpect(status().isOk());
        mvc.perform(post(itemPath + "/edit").session(session)
                        .header("Referer", "http://localhost" + itemPath + "/edit")
                        .param("id", itemId).param("itemName", "edited-item").param("price", "20000").param("quantity", "3"))
                .andExpect(redirectedUrl(itemPath));
        mvc.perform(get(itemPath).session(session)).andExpect(content().string(containsString("edited-item")));
        mvc.perform(post("/space/logout").session(session).header("Referer", "http://localhost/space"))
                .andExpect(redirectedUrl("/"));
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/space/items")).andExpect(redirectedUrl("/space/login?redirectURL=/space/items"));
    }

    @Test
    void newMemberCanRegisterAndLogin() throws Exception {
        mvc.perform(post("/space/members/add").header("Referer", "http://localhost/space/members/add")
                        .param("loginId", "new-member").param("name", "회원").param("password", "example"))
                .andExpect(redirectedUrl("/"));
        mvc.perform(post("/space/login").header("Referer", "http://localhost/space/login")
                        .param("loginId", "new-member").param("password", "example"))
                .andExpect(redirectedUrl("/"))
                .andExpect(request().sessionAttribute(SessionConst.LOGIN_MEMBER, notNullValue()));
    }

    private MockHttpSession login() throws Exception {
        return (MockHttpSession) mvc.perform(post("/space/login")
                        .header("Referer", "http://localhost/space/login")
                        .param("loginId", "test").param("password", "test!")
                        .param("redirectURL", "/space/items"))
                .andExpect(redirectedUrl("/space/items"))
                .andExpect(request().sessionAttribute(SessionConst.LOGIN_MEMBER, notNullValue()))
                .andReturn().getRequest().getSession(false);
    }
}
