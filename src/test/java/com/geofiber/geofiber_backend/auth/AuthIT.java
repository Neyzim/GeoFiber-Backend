package com.geofiber.geofiber_backend.auth;

import com.geofiber.geofiber_backend.enums.user.UserRoleEnum;
import com.geofiber.geofiber_backend.services.auth.UserService;
import com.geofiber.geofiber_backend.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIT extends IntegrationTest {
    private static final String EMAIL = "membro@teste.com";
    private static final String PASSWORD = "senha-membro-123";
    private static final String COOKIE = "geofiber_refresh";
    private static final String LOGIN = "/api/v1/auth/login";
    private static final String REFRESH = "/api/v1/auth/refresh";
    private static final String LOGOUT = "/api/v1/auth/logout";

    @Autowired
    private UserService userService;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void createMember() {
        userService.create("Membro", EMAIL, PASSWORD, UserRoleEnum.MEMBER);
    }

    @Test
    void loginReturnsAccessTokenAndHttpOnlyRefreshCookieStoredAsHash() throws Exception {
        MvcResult result = login(EMAIL, PASSWORD).andExpect(status().isOk()).andReturn();

        assertNotNull(JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken"));
        assertEquals(900, (int) JsonPath.read(result.getResponse().getContentAsString(), "$.expiresIn"));
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertTrue(setCookie.contains(COOKIE + "="));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("Secure"));
        assertTrue(setCookie.contains("SameSite=Strict"));
        assertTrue(setCookie.contains("Path=/api/v1/auth"));
        String raw = refreshCookie(result);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM refresh_token WHERE token_hash = ?",
            Integer.class, raw));
    }

    @Test
    void wrongPasswordAndUnknownEmailReturnTheSameProblem() throws Exception {
        String wrongPassword = login(EMAIL, "senha-errada-123").andExpect(status().isUnauthorized())
            .andReturn().getResponse().getContentAsString();
        String unknownEmail = login("ninguem@teste.com", PASSWORD).andExpect(status().isUnauthorized())
            .andReturn().getResponse().getContentAsString();

        assertEquals(JsonPath.<String>read(wrongPassword, "$.detail"),
            JsonPath.<String>read(unknownEmail, "$.detail"));
    }

    @Test
    void fiveWrongPasswordsBlockTheLoginForFifteenMinutes() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(EMAIL, "senha-errada-123").andExpect(status().isUnauthorized());
        }

        MvcResult blocked = login(EMAIL, PASSWORD).andExpect(status().isTooManyRequests()).andReturn();

        assertEquals("900", blocked.getResponse().getHeader("Retry-After"));
        assertEquals("Muitas tentativas de entrar. Espere 15 minutos e tente de novo.",
            JsonPath.read(blocked.getResponse().getContentAsString(), "$.detail"));
        userService.create("Outra", "outra@teste.com", PASSWORD, UserRoleEnum.MEMBER);
        login("outra@teste.com", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void refreshRotatesTheRefreshToken() throws Exception {
        String first = refreshCookie(login(EMAIL, PASSWORD).andReturn());

        String second = refreshCookie(refresh(first).andExpect(status().isOk()).andReturn());

        assertNotEquals(first, second);
    }

    @Test
    void reuseOfRotatedTokenRevokesEveryTokenOfTheUser() throws Exception {
        String first = refreshCookie(login(EMAIL, PASSWORD).andReturn());
        String second = refreshCookie(refresh(first).andExpect(status().isOk()).andReturn());

        refresh(first).andExpect(status().isUnauthorized());

        refresh(second).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheCurrentRefreshToken() throws Exception {
        String raw = refreshCookie(login(EMAIL, PASSWORD).andReturn());

        mockMvc.perform(post(LOGOUT).cookie(new Cookie(COOKIE, raw))).andExpect(status().isNoContent());

        refresh(raw).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutCookieIsUnauthorized() throws Exception {
        mockMvc.perform(post(REFRESH)).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredRefreshTokenIsUnauthorized() throws Exception {
        String raw = refreshCookie(login(EMAIL, PASSWORD).andReturn());
        jdbc.update("UPDATE refresh_token SET expires_at = now() - interval '1 day'");

        refresh(raw).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointAcceptsTheIssuedAccessToken() throws Exception {
        String token = JsonPath.read(login(EMAIL, PASSWORD).andReturn().getResponse().getContentAsString(),
            "$.accessToken");

        mockMvc.perform(get("/api/v1/boxes").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(json(post(LOGIN), "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String raw) throws Exception {
        return mockMvc.perform(post(REFRESH).cookie(new Cookie(COOKIE, raw)));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /** Lê o valor do refresh token direto do header Set-Cookie, como o navegador o recebe. */
    private static String refreshCookie(MvcResult result) {
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        return setCookie.substring((COOKIE + "=").length(), setCookie.indexOf(';'));
    }
}
