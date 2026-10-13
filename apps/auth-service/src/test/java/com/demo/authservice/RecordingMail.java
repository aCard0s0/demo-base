package com.demo.authservice;

import com.demo.authservice.user.UserService;
import com.demo.authservice.user.entities.User;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A mail sender that keeps what it is handed, for the contexts that sign users up: signup and every change of
 * email mail a link, and a deployment without a mail sender refuses them. {@code @Import} it.
 */
@TestConfiguration
public class RecordingMail {

    public static final List<SimpleMailMessage> SENT = new CopyOnWriteArrayList<>();

    @Bean
    JavaMailSenderImpl mailSender() {
        return new JavaMailSenderImpl() {
            @Override
            public void send(SimpleMailMessage... messages) {
                SENT.addAll(Arrays.asList(messages));
            }
        };
    }

    /** The newest mail to that address. */
    public static SimpleMailMessage lastTo(String email) {
        return SENT.reversed().stream()
                .filter(m -> Arrays.asList(m.getTo()).contains(email))
                .findFirst().orElseThrow(() -> new AssertionError("no mail to " + email));
    }

    /** The link token in the newest mail to that address. */
    public static String linkTo(String email) {
        return lastTo(email).getText().replaceAll("(?s).*#(?:token|verify)=(\\S+).*", "$1");
    }

    /** Signs up and spends the link, the way someone who opened the mail would. */
    public static User registered(UserService users, String name, String email, String password) {
        users.register(name, email, password);
        return users.verifyEmail(linkTo(email), password);
    }
}
