package com.geofiber.geofiber_backend.user;

import com.geofiber.geofiber_backend.enums.user.UserRoleEnum;
import com.geofiber.geofiber_backend.security.JwtService;
import com.geofiber.geofiber_backend.services.auth.UserService;
import com.geofiber.geofiber_backend.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserAdminIT extends IntegrationTest {
    private static final String ADMIN_EMAIL = "gestor@teste.com";
    private static final String ADMIN_PASSWORD = "senha-gestor-123";
    private static final String MEMBER_EMAIL = "membro@teste.com";
    private static final String MEMBER_PASSWORD = "senha-membro-123";

    @Autowired
    private UserService userService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private MockMvc mockMvc;

    private String adminToken;
    private UUID adminId;
    private UUID memberId;

    @BeforeEach
    void seedUsers() {
        adminId = userService.create("Gestor", ADMIN_EMAIL, ADMIN_PASSWORD, UserRoleEnum.ADMIN).getId();
        memberId = userService.create("Membro", MEMBER_EMAIL, MEMBER_PASSWORD, UserRoleEnum.MEMBER).getId();
        adminToken = jwtService.issue(adminId, UserRoleEnum.ADMIN);
    }

    @Test
    void memberCannotCreateUsers() throws Exception {
        String memberToken = jwtService.issue(memberId, UserRoleEnum.MEMBER);

        createUser(memberToken, "novo@teste.com", "senha-novo-123", "MEMBER")
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCreatesMemberWhoCanLogIn() throws Exception {
        createUser(adminToken, "novo@teste.com", "senha-novo-123", "MEMBER")
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"novo@teste.com\",\"password\":\"senha-novo-123\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void duplicateEmailIsConflict() throws Exception {
        createUser(adminToken, MEMBER_EMAIL, "senha-outra-123", "MEMBER")
            .andExpect(status().isConflict());
    }

    @Test
    void shortPasswordIsBadRequest() throws Exception {
        createUser(adminToken, "curta@teste.com", "curta", "MEMBER")
            .andExpect(status().isBadRequest());
    }

    @Test
    void adminListsUsersWithoutPasswordHash() throws Exception {
        String body = mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains(ADMIN_EMAIL) && body.contains(MEMBER_EMAIL));
        assertFalse(body.contains("passwordHash") || body.contains("password_hash"));
    }

    @Test
    void memberCannotListUsers() throws Exception {
        String memberToken = jwtService.issue(memberId, UserRoleEnum.MEMBER);
        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void passwordBoundaryIsEightCharacters() throws Exception {
        createUser(adminToken, "sete@teste.com", "1234567", "MEMBER")
            .andExpect(status().isBadRequest());
        createUser(adminToken, "oito@teste.com", "12345678", "MEMBER")
            .andExpect(status().isCreated());
    }

    @Test
    void deactivatedMemberCannotRefreshAndLosesSessionAtOnce() throws Exception {
        Cookie cookie = loginCookie(MEMBER_EMAIL, MEMBER_PASSWORD);

        setActive(adminToken, memberId, false).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + MEMBER_EMAIL + "\",\"password\":\"" + MEMBER_PASSWORD + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void accessTokenOfDeactivatedMemberStaysValidUntilItExpires() throws Exception {
        String memberToken = jwtService.issue(memberId, UserRoleEnum.MEMBER);

        setActive(adminToken, memberId, false).andExpect(status().isOk());

        // Documentado no desenho: o access token dura até 15 minutos mesmo após a desativação.
        mockMvc.perform(get("/api/v1/boxes").header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk());
    }

    @Test
    void adminCannotDeactivateItself() throws Exception {
        setActive(adminToken, adminId, false).andExpect(status().isConflict());
    }

    @Test
    void resetPasswordReplacesPasswordAndRevokesSessions() throws Exception {
        Cookie cookie = loginCookie(MEMBER_EMAIL, MEMBER_PASSWORD);

        mockMvc.perform(put("/api/v1/users/" + memberId + "/password")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"senha-trocada-123\"}"))
            .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + MEMBER_EMAIL + "\",\"password\":\"senha-trocada-123\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void unknownUserIsNotFound() throws Exception {
        setActive(adminToken, UUID.randomUUID(), false).andExpect(status().isNotFound());
    }

    private ResultActions createUser(String token, String email, String password, String role) throws Exception {
        return mockMvc.perform(post("/api/v1/users")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Pessoa\",\"email\":\"" + email + "\",\"password\":\"" + password
                + "\",\"role\":\"" + role + "\"}"));
    }

    private ResultActions setActive(String token, UUID id, boolean active) throws Exception {
        return mockMvc.perform(patch("/api/v1/users/" + id + "/active")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"active\":" + active + "}"));
    }

    private Cookie loginCookie(String email, String password) throws Exception {
        String setCookie = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getHeader("Set-Cookie");
        assertTrue(setCookie.startsWith("geofiber_refresh="));
        return new Cookie("geofiber_refresh", setCookie.substring("geofiber_refresh=".length(), setCookie.indexOf(';')));
    }

}
