package com.demo.authservice.user;

import com.demo.authservice.user.entities.User;
import com.demo.web.errors.Bad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;

/**
 * The links that prove someone reads an inbox: at signup, and on every change of email. Sign-in with a provider
 * matches users by email alone, so an address nobody proved is a way into whoever later proves it.
 *
 * <ul>
 *   <li>{@link Purpose#VERIFY}: to a new user's address. Spent together with the password chosen at signup, so
 *       neither the inbox's owner nor whoever typed the address in can verify it alone.</li>
 *   <li>{@link Purpose#AUTHORIZE}: to a provider-only user's current address, the one its owner proved to the
 *       provider. The bearer of a borrowed tab picks the new address; only the owner reads the old one.</li>
 *   <li>{@link Purpose#CHANGE}: to the new address, proving it before it replaces the old.</li>
 * </ul>
 *
 * <p>A link is stateless: its purpose, the user, the address it changes from and to, an expiry, and an HMAC
 * under {@code auth.email-link-key} -- or, left blank, a key made at startup, which voids every link on a restart
 * and makes two instances strangers to each other's links. Naming the address it changes from makes it good once.
 * No mail server configured means each of these is refused, not skipped.
 */
@Component
public class EmailLinks {

    private static final Logger log = LoggerFactory.getLogger(EmailLinks.class);

    /** Long enough to find the mail, short enough that a forgotten one does not wait in an inbox for long. */
    static final Duration TTL = Duration.ofMinutes(30);

    public enum Purpose { VERIFY, AUTHORIZE, CHANGE }

    public record Link(Purpose purpose, long userId, String from, String to) {}

    private final SecretKeySpec key;

    private final ObjectProvider<JavaMailSender> mail;

    private final String baseUrl;

    private final String sender;

    public EmailLinks(ObjectProvider<JavaMailSender> mail,
                      @Value("${oauth.redirect-base-url}") String baseUrl,
                      @Value("${auth.mail-from}") String sender,
                      @Value("${auth.email-link-key:}") String configuredKey) {
        byte[] secret;
        if (configuredKey == null || configuredKey.isBlank()) {
            secret = new byte[32];
            new SecureRandom().nextBytes(secret);
        } else {
            // Any string will do; hashed so its length is the key's, like agent-service's header key.
            try {
                secret = MessageDigest.getInstance("SHA-256").digest(configuredKey.getBytes(StandardCharsets.UTF_8));
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 is part of every JRE", e);
            }
        }
        this.key = new SecretKeySpec(secret, "HmacSHA256");
        this.mail = mail;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.sender = sender;
    }

    /** 503 before anything is written, when the deployment has no mail server to send the link with. */
    public JavaMailSender requireMail() {
        JavaMailSender sender = mail.getIfAvailable();
        if (sender == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "this server cannot send email, so it cannot confirm an address");
        }
        return sender;
    }

    /** The signup link. The login page opens in its confirm mode and asks for the password chosen at signup. */
    public void sendVerification(User user) {
        send(user.getEmail(), "Confirm your email address",
                "Open this link within " + TTL.toMinutes() + " minutes and sign in to finish creating your account:\n\n"
                        + baseUrl + "/login#verify=" + sign(Purpose.VERIFY, user, user.getEmail())
                        + "\n\nIf you did not sign up, ignore this mail; nothing happens without the password.");
    }

    /** To a provider-only user's current address: approve moving to {@code to}, which then gets its own link. */
    public void sendAuthorization(User user, String to) {
        // A page with a button, not a link that acts on load: mail scanners open links on their own.
        send(user.getEmail(), "Approve changing your email address",
                "Someone asked to change the email of your account from " + user.getEmail() + " to " + to
                        + ".\n\nIf it was you, open this link within " + TTL.toMinutes() + " minutes and approve; "
                        + to + " then gets a link of its own to confirm:\n\n"
                        + baseUrl + "/confirm-email#token=" + sign(Purpose.AUTHORIZE, user, to)
                        + "\n\nIf it was not you, ignore this mail; nothing changes, but someone may have had your session.");
    }

    /** To the new address: confirm it, and the change is made. */
    public void sendChange(User user, String to) {
        send(to, "Confirm your new email address",
                "Open this link within " + TTL.toMinutes() + " minutes to make " + to
                        + " the email of your account, instead of " + user.getEmail() + ":\n\n"
                        + baseUrl + "/confirm-email#token=" + sign(Purpose.CHANGE, user, to)
                        + "\n\nIf you did not ask for this, ignore this mail; nothing changes.");
    }

    /**
     * To an address someone else just tried to sign up with, or to move their account to: the owner hears, and the
     * caller is answered as if a link went out, so the API never says which addresses are taken.
     */
    public void sendAlreadyRegistered(String to) {
        send(to, "You already have an account here",
                "Someone just tried to use " + to + " for another account here. It already belongs to yours, so nothing"
                        + " changed.\n\nIf that was you, sign in with your password or your provider instead.");
    }

    /** The link a token names, if it is ours, unaltered, unexpired and one of the {@code allowed} purposes; else 400. */
    public Link read(String token, Purpose... allowed) {
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length != 2 || !MessageDigest.isEqual(mac(parts[0]).getBytes(StandardCharsets.UTF_8), parts[1].getBytes(StandardCharsets.UTF_8))) {
            throw Bad.request("this link is not valid");
        }
        String[] fields = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8).split("\n");
        Purpose purpose = Purpose.valueOf(fields[0]);
        // A signup link posted as a change, or the reverse, is not valid either: each is spent its own way.
        if (!Set.of(allowed).contains(purpose)) {
            throw Bad.request("this link is not valid");
        }
        if (Instant.now().getEpochSecond() > Long.parseLong(fields[2])) {
            throw Bad.request("this link has expired, ask for a new one");
        }
        return new Link(purpose, Long.parseLong(fields[1]), fields[3], fields[4]);
    }

    /** Newline-separated: a clean email cannot hold whitespace, so no field can smuggle in another. */
    private String sign(Purpose purpose, User user, String to) {
        String expires = String.valueOf(Instant.now().plus(TTL).getEpochSecond());
        String payload = String.join("\n", purpose.name(), String.valueOf(user.getId()), expires, user.getEmail(), to);
        String body = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return body + "." + mac(body);
    }

    private void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            requireMail().send(message);
        } catch (MailException e) {
            log.warn("could not send \"{}\"", subject, e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "could not send the email, try again later");
        }
    }

    private String mac(String body) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(key);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is part of every JRE", e);
        }
    }
}
