package com.demo.authservice.user;

import com.demo.authservice.user.dto.EmailChange;
import com.demo.authservice.user.dto.EmailConfirmation;
import com.demo.authservice.user.dto.EmailStep;
import com.demo.authservice.user.dto.NewUser;
import com.demo.authservice.user.dto.RoleChange;
import com.demo.authservice.user.dto.Suspension;
import com.demo.authservice.user.dto.UpdateUser;
import com.demo.authservice.user.dto.UserResponse;
import com.demo.authservice.user.entities.Role;
import com.demo.authservice.user.entities.User;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Who is asking is the {@link User} parameter, resolved from the Authorization header by
 * {@link CurrentUserResolver} before any method here runs; registration is the one endpoint without it.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    /**
     * Only the address comes back: the user is not signed in until its link is spent, and an address that is
     * already somebody's is answered exactly like a free one (its owner is mailed instead).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> register(@RequestBody NewUser req) {
        return Map.of("email", users.register(req.name(), req.email(), req.password()).getEmail());
    }

    /** The caller themselves. Whoever holds the token is the only one who can ask. */
    @GetMapping("/me")
    public UserResponse me(User caller) {
        return UserResponse.of(caller);
    }

    /**
     * Edits the caller. The answer carries a fresh token: a new password revokes every token the user held,
     * this one included, and the page swaps to the new one rather than being signed out mid-edit. Only when a
     * password was checked, though: for a provider-only user a rename would otherwise renew a stolen token forever.
     */
    @PutMapping("/me")
    public UserResponse update(User caller, @RequestBody UpdateUser req) {
        User updated = users.update(caller.getId(), req.name(), req.email(), req.currentPassword(), req.newPassword());
        return UserResponse.of(updated, updated.isPasswordSet() ? users.issue(updated) : null);
    }

    /** A provider-only user's new email: answers with its current address, where the approval link was mailed. */
    @PostMapping("/me/email")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EmailChange requestEmailChange(User caller, @RequestBody EmailChange req) {
        users.requestEmailChange(caller.getId(), req.email());
        return new EmailChange(caller.getEmail());
    }

    /**
     * A change-of-email link, posted by the frontend's confirm page. No caller: holding the link is the proof,
     * since only the inbox it was mailed to received it.
     */
    @PostMapping("/email-confirm")
    public EmailStep confirmEmailChange(@RequestBody EmailConfirmation req) {
        return users.confirmEmailChange(req.token());
    }

    /** Everyone, for the roles that read everyone. A user asking for this gets a 403, not a filtered list. */
    @GetMapping
    public List<UserResponse> all(User caller) {
        if (!caller.getRole().readsEveryone()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "you may only read yourself");
        }
        return users.all().stream().map(UserResponse::of).toList();
    }

    /**
     * Promotes or demotes another user. Admin only, and the only route off USER: registration cannot ask
     * for a role and {@link #update} cannot change one, so this endpoint is the single door.
     */
    @PutMapping("/{id}/role")
    public UserResponse setRole(User caller, @PathVariable Long id, @RequestBody RoleChange req) {
        return UserResponse.of(users.changeRole(admin(caller), id, req.role()));
    }

    /** Suspends or reactivates another user. Admin only. */
    @PutMapping("/{id}/suspended")
    public UserResponse setSuspended(User caller, @PathVariable Long id, @RequestBody Suspension req) {
        return UserResponse.of(users.setSuspended(admin(caller), id, req.suspended()));
    }

    /** Signs another user out everywhere by killing every token it holds. Admin only. */
    @PostMapping("/{id}/revoke")
    public UserResponse revoke(User caller, @PathVariable Long id) {
        admin(caller);
        return UserResponse.of(users.revokeTokens(id));
    }

    private static User admin(User caller) {
        if (caller.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only an admin can do that");
        }
        return caller;
    }
}
