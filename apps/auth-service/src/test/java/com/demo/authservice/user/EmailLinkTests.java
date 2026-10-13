package com.demo.authservice.user;

import com.demo.authservice.RecordingMail;
import com.demo.authservice.session.SessionService;
import com.demo.authservice.user.entities.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

import static com.demo.authservice.RecordingMail.linkTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every address proved by the inbox it names, over HTTP, against a mail sender that keeps what it is handed: a
 * signup is finished by its link plus its password, an unproven address is free for its owner, and every change of
 * email waits for the inbox (both inboxes, for a user with no password to prove itself with).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-email-links.db",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false",
        "spring.datasource.hikari.maximum-pool-size=1",
})
@AutoConfigureMockMvc
@Import(RecordingMail.class)
class EmailLinkTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    UserService users;

    @Autowired
    SessionService sessions;

    @Test
    void aSignupIsFinishedByItsLinkAndItsPasswordTogether() throws Exception {
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ada\",\"email\":\"ada@example.com\",\"password\":\"lovelace-1815\"}"))
                .andExpect(status().isCreated());
        String link = linkTo("ada@example.com");

        mvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ada@example.com\",\"password\":\"lovelace-1815\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("confirm your email first: we sent a new link to ada@example.com"));
        verify(link, "not-her-password").andExpect(status().isBadRequest());
        verify(link, "lovelace-1815").andExpect(status().isOk()).andExpect(jsonPath("$.token").isString());
        verify(link, "lovelace-1815").andExpect(status().isBadRequest());
        assertTrue(sessions.login("ada@example.com", "lovelace-1815").isPresent());

        mvc.perform(post("/api/users/email-confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + link + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aSignupLinkIsNotAnUnlimitedPasswordCheck() throws Exception {
        users.register("Alan", "alan@example.com", "turing-1912");
        String link = linkTo("alan@example.com");
        for (int i = 0; i < 5; i++) {
            verify(link, "guess-" + i).andExpect(status().isBadRequest());
        }
        // Locked out even with the right password, like a login.
        verify(link, "turing-1912").andExpect(status().isTooManyRequests());
    }

    @Test
    void anUnprovenAddressIsFreeForItsOwnerAndTheSquattersPasswordGoes() {
        users.register("Squatter", "grace@example.com", "squatter-pass");
        String squattersLink = linkTo("grace@example.com");
        users.register("Grace", "grace@example.com", "hopper-1906");

        assertThrows(ResponseStatusException.class, () -> users.verifyEmail(squattersLink, "squatter-pass"),
                "the inbox's owner signed up over it: the squatter's password no longer verifies anything");
        User grace = users.verifyEmail(linkTo("grace@example.com"), "hopper-1906");
        assertEquals("Grace", grace.getName());
        // A proven address is taken, but the API says so only to its inbox: the answer looks like any signup.
        users.register("Again", "grace@example.com", "another-pass");
        assertEquals("You already have an account here", RecordingMail.lastTo("grace@example.com").getSubject());
        assertTrue(sessions.login("grace@example.com", "hopper-1906").isPresent(), "the owner's password is untouched");
    }

    @Test
    void aProviderSignInClaimsAnUnprovenUserAndWipesItsPassword() {
        User squatted = users.register("Squatter", "linus@example.com", "squatter-pass");

        User claimed = users.findOrCreateFromOAuth("linus@example.com", "Linus");
        assertEquals(squatted.getId(), claimed.getId());
        assertEquals("Linus", claimed.getName());
        assertTrue(claimed.isEmailVerified());
        assertFalse(claimed.isPasswordSet());
        assertTrue(sessions.login("linus@example.com", "squatter-pass").isEmpty(), "the squatter's password is gone");
    }

    @Test
    void aPasswordUsersNewEmailWaitsForTheLinkMailedToIt() throws Exception {
        User edsger = RecordingMail.registered(users, "Edsger", "edsger@example.com", "dijkstra-1930");
        String bearer = "Bearer " + users.issue(edsger);

        mvc.perform(put("/api/users/me").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Edsger\",\"email\":\"ewd@example.com\",\"currentPassword\":\"dijkstra-1930\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("edsger@example.com"));
        String link = linkTo("ewd@example.com");

        confirm(link).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ewd@example.com")).andExpect(jsonPath("$.changed").value(true));
        confirm(link).andExpect(status().isBadRequest());
        mvc.perform(post("/api/users/me/email").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"other@example.com\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aProviderOnlyUsersNewEmailNeedsTheOldInboxThenTheNewOne() throws Exception {
        User barbara = users.findOrCreateFromOAuth("barbara@example.com", "Barbara");
        String bearer = "Bearer " + users.issue(barbara);
        mvc.perform(get("/api/users/me").header("Authorization", bearer))
                .andExpect(jsonPath("$.passwordSet").value(false));

        // A rename needs no password, and hands back no fresh token a thief could keep renewing.
        mvc.perform(put("/api/users/me").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Barbara L\",\"email\":\"barbara@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").doesNotExist());
        mvc.perform(put("/api/users/me").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Barbara L\",\"email\":\"thief@example.com\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/users/me/email").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Liskov@Example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.email").value("barbara@example.com"));
        String approval = linkTo("barbara@example.com");
        assertTrue(RecordingMail.SENT.stream().noneMatch(m -> Arrays.asList(m.getTo()).contains("liskov@example.com")),
                "nothing goes to the new address until the old inbox approves");

        confirm(approval.substring(0, approval.length() - 2) + "xx").andExpect(status().isBadRequest());
        confirm(approval).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("liskov@example.com")).andExpect(jsonPath("$.changed").value(false));
        mvc.perform(get("/api/users/me").header("Authorization", bearer))
                .andExpect(jsonPath("$.email").value("barbara@example.com"));

        String change = linkTo("liskov@example.com");
        confirm(change).andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(true));
        confirm(change).andExpect(status().isBadRequest());
        confirm(approval).andExpect(status().isBadRequest());
        mvc.perform(get("/api/users/me").header("Authorization", bearer))
                .andExpect(jsonPath("$.email").value("liskov@example.com"));
    }

    private ResultActions verify(String token, String password) throws Exception {
        return mvc.perform(post("/api/login/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions confirm(String token) throws Exception {
        return mvc.perform(post("/api/users/email-confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"));
    }
}
