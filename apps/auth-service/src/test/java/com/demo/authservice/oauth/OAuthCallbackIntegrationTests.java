package com.demo.authservice.oauth;

import com.demo.auth.provider.Identity;
import com.demo.auth.provider.OAuthProvider;
import com.demo.authservice.user.UserService;
import com.demo.authservice.user.entities.Role;
import com.demo.authservice.user.entities.User;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import com.demo.authservice.RecordingMail;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The other half of the provider flow, the one {@link OAuthTests} leaves out: the callback with a real code,
 * exchanged over real HTTP against a throwaway server standing in for the provider, and what this service
 * then does with the identity -- find or make the user, refuse a suspended one, mint a token, hand it over
 * only to the browser that walked the flow, and spend the cookies whatever happened.
 *
 * <p>Two providers are registered for the test alone, both pointing at the throwaway server: one that
 * answers with a query string like Google, and one that answers with a form POST like Apple.
 */
@Import(RecordingMail.class)
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:target/test-oauth-callback.db",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false",
        "spring.datasource.hikari.maximum-pool-size=1",
        "oauth.redirect-base-url=https://app.example.com",
})
@AutoConfigureMockMvc
class OAuthCallbackIntegrationTests {

    /** Started before the context, because the providers need its address at construction. */
    static final HttpServer PROVIDER = start();

    static final Map<String, String> SEEN = new ConcurrentHashMap<>();

    static volatile String tokenAnswer = "{\"access_token\":\"at-1\"}";

    static volatile String meAnswer = "{\"email\":\"ada@example.com\",\"name\":\"Ada\"}";

    static class Local extends OAuthProvider {
        Local(String key, String base) {
            super(key, key, base + "/authorize", base + "/token", "email");
            setEnabled(true);
            setClientId("client-1");
            setClientSecret("secret-1");
        }

        @Override
        public Identity identity(RestClient http, String accessToken) {
            Map<?, ?> me = get(http, getAuthorizeUri().replace("/authorize", "/me"), accessToken, Map.class);
            return new Identity(string(me, "email"), string(me, "name"));
        }
    }

    static final class FormPost extends Local {
        FormPost(String base) {
            super("formpost", base);
        }

        @Override
        public boolean formPost() {
            return true;
        }
    }

    @TestConfiguration
    static class Providers {
        @Bean
        OAuthProvider local() {
            return new Local("local", "http://localhost:" + PROVIDER.getAddress().getPort());
        }

        @Bean
        OAuthProvider formPost() {
            return new FormPost("http://localhost:" + PROVIDER.getAddress().getPort());
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    UserService users;

    @BeforeEach
    void reset() {
        SEEN.clear();
        tokenAnswer = "{\"access_token\":\"at-1\"}";
        meAnswer = "{\"email\":\"ada@example.com\",\"name\":\"Ada\"}";
    }

    @AfterAll
    static void stop() {
        PROVIDER.stop(0);
    }

    @Test
    void aCodeThatStartedHereBuysATokenForTheUserBehindTheVerifiedEmail() throws Exception {
        MockHttpServletResponse started = mvc.perform(get("/api/oauth/local/start")).andExpect(status().isFound())
                .andReturn().getResponse();
        String state = started.getCookie("__Host-oauth_state").getValue();
        String verifier = started.getCookie("__Host-oauth_verifier").getValue();
        assertTrue(started.getHeaders("Set-Cookie").stream().allMatch(c -> c.startsWith("__Host-") && c.contains("Path=/;")
                        && c.contains("SameSite=Lax") && c.contains("Secure")),
                "a query-string provider gets Lax cookies, Secure and __Host- because the base is https, so no sibling "
                        + "subdomain can plant its own: " + started.getHeaders("Set-Cookie"));

        MockHttpServletResponse back = mvc.perform(get("/api/oauth/local/callback").param("code", "code-1").param("state", state)
                        .cookie(new Cookie("__Host-oauth_state", state), new Cookie("__Host-oauth_verifier", verifier)))
                .andExpect(status().isFound())
                .andReturn().getResponse();

        // The exchange that reached the provider carried the verifier the browser kept, and our secret.
        Map<String, String> form = form(SEEN.get("token.form"));
        assertEquals("code-1", form.get("code"));
        assertEquals(verifier, form.get("code_verifier"));
        assertEquals("secret-1", form.get("client_secret"));
        assertEquals("https://app.example.com/api/oauth/local/callback", form.get("redirect_uri"));
        assertEquals("Bearer at-1", SEEN.get("me.authorization"));

        // The browser lands on the login page told only that it worked: the token is in no URL, so a link cannot
        // carry one into somebody else's browser.
        assertEquals("https://app.example.com/login#oauth=done", back.getHeader("Location"));
        String handOff = back.getHeaders("Set-Cookie").stream().filter(c -> c.startsWith("__Host-oauth_session=")).findFirst().orElseThrow();
        for (String attribute : List.of("Path=/;", "Max-Age=60", "HttpOnly", "SameSite=Strict", "Secure")) {
            assertTrue(handOff.contains(attribute), attribute + " missing from " + handOff);
        }
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + collect(back)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.name").value("Ada"));

        // And the state cookies are spent, so the same code cannot be replayed from this browser either.
        assertTrue(back.getHeaders("Set-Cookie").stream().filter(c -> !c.startsWith("__Host-oauth_session="))
                .allMatch(c -> c.contains("Max-Age=0")), back.getHeaders("Set-Cookie").toString());
        mvc.perform(get("/api/oauth/local/callback").param("code", "code-1").param("state", state))
                .andExpect(header().string("Location", "https://app.example.com/login#error=" + encode("not-started")));
    }

    @Test
    void anExistingUserIsFoundByEmailAndASuspendedOneIsRefused() throws Exception {
        User grace = RecordingMail.registered(users, "Grace", "grace@example.com", "hopper-1906");
        meAnswer = "{\"email\":\"GRACE@example.com\",\"name\":\"G. Hopper\"}";

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + collect(finish("local"))))
                .andExpect(jsonPath("$.id").value(grace.getId()))
                .andExpect(jsonPath("$.name").value("Grace")); // the name set here wins over the provider's

        User admin = users.ensureAdmin("chief@example.com", "chief-pass-01");
        users.setSuspended(admin, grace.getId(), true);
        assertEquals("https://app.example.com/login#error=" + encode("suspended"), callback("local"));
    }

    @Test
    void whatTheProviderSaysWhenItFailsStaysOutOfTheBrowser() throws Exception {
        tokenAnswer = "{\"error\":\"invalid_grant\",\"error_description\":\"internal detail about client-1\"}";
        String location = callback("local");
        assertEquals("https://app.example.com/login#error=" + encode("failed"), location);
        assertNull(SEEN.get("me.authorization"), "no token, so the API was never asked");

        // The user cancelling at the consent screen never reaches the provider's back channel at all.
        SEEN.clear();
        MockHttpServletResponse started = mvc.perform(get("/api/oauth/local/start")).andReturn().getResponse();
        mvc.perform(get("/api/oauth/local/callback").param("error", "access_denied").param("state", started.getCookie("__Host-oauth_state").getValue())
                        .cookie(started.getCookies()))
                .andExpect(header().string("Location", "https://app.example.com/login#error=" + encode("cancelled")));
        assertNull(SEEN.get("token.form"));

        // An identity the provider will not vouch for is the flat message too, not the provider's words.
        meAnswer = "{\"email\":\"\",\"name\":\"Nobody\"}";
        assertEquals("https://app.example.com/login#error=" + encode("failed"), callback("local"));
    }

    @Test
    void aFormPostProviderComesBackWithAPostAndCookiesThatSurviveIt() throws Exception {
        MockHttpServletResponse started = mvc.perform(get("/api/oauth/formpost/start")).andExpect(status().isFound())
                .andReturn().getResponse();
        assertTrue(started.getHeader("Location").endsWith("&response_mode=form_post"), started.getHeader("Location"));
        assertTrue(started.getHeaders("Set-Cookie").stream().allMatch(c -> c.contains("SameSite=None") && c.contains("Secure")),
                "a cross-site POST carries no Lax cookie, so these must be None, and None must be Secure: " + started.getHeaders("Set-Cookie"));
        String state = started.getCookie("__Host-oauth_state").getValue();

        String location = mvc.perform(post("/api/oauth/formpost/callback").param("code", "code-9").param("state", state)
                        .contentType("application/x-www-form-urlencoded").cookie(started.getCookies()))
                .andExpect(status().isFound())
                .andReturn().getResponse().getHeader("Location");
        assertEquals("https://app.example.com/login#oauth=done", location);
        assertEquals("code-9", form(SEEN.get("token.form")).get("code"));

        // A POST that did not start here is refused the same as a GET.
        mvc.perform(post("/api/oauth/formpost/callback").param("code", "code-9").param("state", state))
                .andExpect(header().string("Location", "https://app.example.com/login#error=" + encode("not-started")));
    }

    @Test
    void theTokenGoesOnlyToTheBrowserHoldingTheHandOffCookieAndOnlyOnce() throws Exception {
        MockHttpServletResponse back = finish("local");
        Cookie handOff = back.getCookie("__Host-oauth_session");

        // A browser that did not walk the flow -- the victim of a forged link -- has nothing to collect, and over
        // https a bare-named cookie, which a sibling subdomain could have planted, is not ours.
        mvc.perform(post("/api/oauth/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("no sign-in to finish here, try again"));
        mvc.perform(post("/api/oauth/session").cookie(new Cookie("oauth_session", handOff.getValue())))
                .andExpect(status().isUnauthorized());

        MockHttpServletResponse collected = mvc.perform(post("/api/oauth/session").cookie(handOff))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse();
        assertTrue(collected.getHeader("Set-Cookie").startsWith("__Host-oauth_session=;"), collected.getHeader("Set-Cookie"));
        assertTrue(collected.getHeader("Set-Cookie").contains("Max-Age=0"), collected.getHeader("Set-Cookie"));

        // A failed sign-in plants nothing to collect.
        tokenAnswer = "{\"error\":\"invalid_grant\"}";
        assertNull(finish("local").getCookie("__Host-oauth_session"));
    }

    /** Start and finish in one go, with the cookies carried over, and give back where the browser lands. */
    private String callback(String provider) throws Exception {
        return finish(provider).getHeader("Location");
    }

    private MockHttpServletResponse finish(String provider) throws Exception {
        MockHttpServletResponse started = mvc.perform(get("/api/oauth/" + provider + "/start")).andReturn().getResponse();
        return mvc.perform(get("/api/oauth/" + provider + "/callback").param("code", "code-1")
                        .param("state", started.getCookie("__Host-oauth_state").getValue()).cookie(started.getCookies()))
                .andExpect(status().isFound())
                .andReturn().getResponse();
    }

    /** What the login page does on #oauth=done: trade the hand-off cookie for the token. */
    private String collect(MockHttpServletResponse back) throws Exception {
        String body = mvc.perform(post("/api/oauth/session").cookie(back.getCookie("__Host-oauth_session")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.token");
    }

    private static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/token", exchange -> {
                SEEN.put("token.form", new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                answer(exchange, tokenAnswer);
            });
            server.createContext("/me", exchange -> {
                SEEN.put("me.authorization", exchange.getRequestHeaders().getFirst("Authorization"));
                answer(exchange, meAnswer);
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void answer(HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static Map<String, String> form(String body) {
        Map<String, String> pairs = new ConcurrentHashMap<>();
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            pairs.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return pairs;
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
