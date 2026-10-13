package com.demo.authservice.session;

import com.demo.authservice.user.UserService;
import com.demo.authservice.user.dto.EmailVerification;
import com.demo.authservice.user.entities.User;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class SessionController {

    private final SessionService sessions;

    private final UserService users;

    public SessionController(SessionService sessions, UserService users) {
        this.sessions = sessions;
        this.users = users;
    }

    @PostMapping("/api/login")
    public LoginResponse login(@RequestBody LoginRequest req) {
        Session session = sessions.login(req.email(), req.password())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"));
        User user = session.user();
        return new LoginResponse(session.token(), user.getName(), user.getEmail(), user.getRole());
    }

    /**
     * Finishes a signup: the mailed link plus the password chosen at signup, and the answer is a login. No token:
     * the link and the password are the proof.
     */
    @PostMapping("/api/login/verify-email")
    public LoginResponse verifyEmail(@RequestBody EmailVerification req) {
        User user = users.verifyEmail(req.token(), req.password());
        return new LoginResponse(users.issue(user), user.getName(), user.getEmail(), user.getRole());
    }
}
