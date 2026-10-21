package com.demo.authservice.token;

import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A configured signing key outlives a restart. */
class TokensTests {

    private static String rsaKey() throws Exception {
        return rsaKey(2048);
    }

    private static String rsaKey(int bits) throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(bits);
        return Base64.getEncoder().encodeToString(g.generateKeyPair().getPrivate().getEncoded());
    }

    private static Object kid(Tokens tokens) {
        return ((Map<?, ?>) ((List<?>) tokens.publicJwks().get("keys")).get(0)).get("kid");
    }

    @Test
    void twoInstancesOnTheSameKeyVerifyEachOthersTokens() throws Exception {
        String key = rsaKey();
        Tokens before = new Tokens(Duration.ofMinutes(30), Duration.ofDays(30), key);
        Tokens after = new Tokens(Duration.ofMinutes(30), Duration.ofDays(30), key);

        String agentToken = before.issueForAgent(2L, "a@b.c", "A", 0, 7L, 0);

        assertEquals(new Tokens.Claims(2L, 0, true), after.claimsFrom(agentToken).orElseThrow());
        assertEquals(kid(before), kid(after));
    }

    @Test
    void aBlankKeyIsMadeAtStartupAndDiesWithIt() throws Exception {
        Tokens before = new Tokens(Duration.ofMinutes(30));
        Tokens after = new Tokens(Duration.ofMinutes(30));

        assertTrue(after.claimsFrom(before.issue(1L, "a@b.c", "A", "USER", 0)).isEmpty());
    }

    @Test
    void aWrappedKeyStillParses() throws Exception {
        String key = rsaKey();
        Tokens wrapped = new Tokens(Duration.ofMinutes(30), Duration.ofDays(30), key.replaceAll("(.{76})", "$1\n"));

        assertEquals(kid(new Tokens(Duration.ofMinutes(30), Duration.ofDays(30), key)), kid(wrapped));
    }

    @Test
    void aWeakKeyRefusesToStart() throws Exception {
        String weak = rsaKey(1024);
        assertThrows(IllegalStateException.class, () -> new Tokens(Duration.ofMinutes(30), Duration.ofDays(30), weak));
    }

    @Test
    void aKeyThatDoesNotParseRefusesToStart() {
        assertThrows(IllegalStateException.class, () -> new Tokens(Duration.ofMinutes(30), Duration.ofDays(30), "not-a-key"));
    }
}
