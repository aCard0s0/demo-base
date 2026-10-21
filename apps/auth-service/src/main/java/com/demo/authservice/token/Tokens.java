package com.demo.authservice.token;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Base64;
import java.util.Optional;

/**
 * Issues and checks the RS256 tokens that identify a caller.
 *
 * <p>The private key comes from {@code auth.signing-key} (base64 PKCS#8, {@code AUTH_SIGNING_KEY} in .env):
 * auth-service is the only holder of it, and every other service reads the public half from the JWKS endpoint.
 * Asymmetric rather than a shared secret, so a service that only needs to verify tokens is never able to mint them.
 * The kid is the key's thumbprint, so the same key publishes the same kid across restarts.
 *
 * <p>Left blank, a key is made at startup, so a restart invalidates every token in flight, the 30-day agent
 * tokens included. Fine for tests and a bare {@code spring-boot:run}; {@code ./dbase setup} writes one to .env.
 */
// ponytail: one key, no rotation. Tokens carry a kid, so publishing a previous key beside it later is additive.
@Component
public class Tokens {

    private static final Logger log = LoggerFactory.getLogger(Tokens.class);

    private final RSAKey key;

    private final RSASSASigner signer;

    private final RSASSAVerifier verifier;

    private final Duration ttl;

    private final Duration agentTtl;

    /** Login tokens of the given life, agent tokens of the default 30 days. */
    public Tokens(Duration ttl) throws Exception {
        this(ttl, Duration.ofDays(30), "");
    }

    @Autowired
    public Tokens(@Value("${auth.token-ttl:30m}") Duration ttl,
                  @Value("${auth.agent-token-ttl:30d}") Duration agentTtl,
                  @Value("${auth.signing-key:}") String signingKey) throws Exception {
        this.ttl = ttl;
        this.agentTtl = agentTtl;
        if (signingKey == null || signingKey.isBlank()) {
            log.warn("auth.signing-key is blank: signing with a key made at startup, so a restart voids every token");
            this.key = new RSAKeyGenerator(2048).keyIDFromThumbprint(true).generate();
        } else {
            this.key = parse(signingKey);
        }
        try {
            // Built here so a key under 2048 bits refuses to start rather than failing the first login.
            this.signer = new RSASSASigner(key);
        } catch (Exception e) {
            throw new IllegalStateException("auth.signing-key is too weak: RSA 2048 bits or more", e);
        }
        this.verifier = new RSASSAVerifier(key.toPublicJWK());
    }

    /** The RSA key a base64 PKCS#8 private key names, its public half rebuilt from the CRT fields. */
    private static RSAKey parse(String signingKey) {
        try {
            KeyFactory rsa = KeyFactory.getInstance("RSA");
            RSAPrivateCrtKey priv = (RSAPrivateCrtKey) rsa.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(signingKey)));
            RSAPublicKey pub = (RSAPublicKey) rsa.generatePublic(new RSAPublicKeySpec(priv.getModulus(), priv.getPublicExponent()));
            return new RSAKey.Builder(pub).privateKey(priv).keyIDFromThumbprint().build();
        } catch (Exception e) {
            throw new IllegalStateException("auth.signing-key is not a base64 PKCS#8 RSA private key", e);
        }
    }

    /**
     * A signed token naming the user. The id is the subject because it is the one thing that never changes.
     *
     * <p>Takes the fields rather than the User itself, so this package stays free of any dependency on
     * the user package and the arrow between them only ever points one way. The role rides along as a
     * plain string: it is what lets agent-service decide what a caller may touch without asking us.
     */
    public String issue(Long userId, String email, String name, String role, int version) {
        return issue(userId, email, name, role, version, Map.of(), ttl);
    }

    /**
     * A long-lived token for one of the user's agents to connect to agent-service with: the user as
     * subject, the {@code AGENT} role, and the agent's id as a claim so agent-service can pin it to that one
     * agent. It carries the user's current token version like any other, so "revoke access" kills it too,
     * and the agent's own version as {@code agentVer}, so the owner can kill this one agent's tokens alone.
     */
    public String issueForAgent(Long userId, String email, String name, int version, Long agentId, int agentVersion) {
        return issue(userId, email, name, "AGENT", version, Map.of("agent", agentId, "agentVer", agentVersion), agentTtl);
    }

    private String issue(Long userId, String email, String name, String role, int version,
                         Map<String, Object> extra, Duration lifetime) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                // Checked exactly by auth-client's JwtVerifier.
                .issuer("auth-service")
                .audience("demo-base")
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("name", name)
                .claim("role", role)
                .claim("ver", version)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(lifetime)));
        extra.forEach(claims::claim);
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims.build());
        try {
            jwt.sign(signer);
        } catch (Exception e) {
            throw new IllegalStateException("could not sign a token", e);
        }
        return jwt.serialize();
    }

    /** What a verified token says about its user: which one, which generation of its tokens, and whether it was minted for an agent. */
    public record Claims(Long userId, int version, boolean agent) {}

    /** What a token names, or empty if the signature is wrong, the token is malformed, or it has expired. */
    public Optional<Claims> claimsFrom(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(verifier)) {
                return Optional.empty();
            }
            Date expiry = jwt.getJWTClaimsSet().getExpirationTime();
            if (expiry == null || expiry.toInstant().isBefore(Instant.now())) {
                return Optional.empty();
            }
            Long version = jwt.getJWTClaimsSet().getLongClaim("ver");
            return Optional.of(new Claims(Long.valueOf(jwt.getJWTClaimsSet().getSubject()),
                    version == null ? 0 : version.intValue(), "AGENT".equals(jwt.getJWTClaimsSet().getStringClaim("role"))));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** The public half, in the shape the JWKS endpoint serves and other services know how to read. */
    public Map<String, Object> publicJwks() {
        return new JWKSet(key.toPublicJWK()).toJSONObject();
    }
}
