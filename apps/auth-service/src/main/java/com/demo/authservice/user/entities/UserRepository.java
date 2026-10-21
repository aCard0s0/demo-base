package com.demo.authservice.user.entities;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** The two columns the revocation feed serves, and nothing else of the row. */
    record TokenVersion(Long id, int tokenVersion) {}

    /**
     * The caller's own edit, as one statement. Conditional on the hash the caller was just checked against, so
     * a password changed in between makes this a no-op (0 rows) rather than a silent overwrite; and the version
     * bump is relative, so a revoke that lands in between is kept rather than written back stale.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update User u set u.name = :name, u.email = :email, u.passwordHash = :hash, "
            + "u.tokenVersion = u.tokenVersion + :bump where u.id = :id and u.passwordHash = :expectedHash")
    int edit(Long id, String name, String email, String expectedHash, String hash, int bump);

    /** Marks the email proven, unless the password changed since it was checked: then 0 and nothing happens. */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update User u set u.emailVerified = true where u.id = :id and u.passwordHash = :expectedHash")
    int verifyEmail(Long id, String expectedHash);

    /** A signup over an address nobody proved yet: the new name and password replace the old. 0 once proven. */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update User u set u.name = :name, u.passwordHash = :hash where u.email = :email and u.emailVerified = false")
    int reRegister(String email, String name, String hash);

    /**
     * An admin's new user over an address nobody proved: the row becomes that user, proven, active and with the
     * admin's password and role, and anything ever signed for it dies. 0 if it was proven in the meantime.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update User u set u.name = :name, u.passwordHash = :hash, u.passwordSet = true, u.emailVerified = true, "
            + "u.suspended = false, u.role = :role, u.tokenVersion = u.tokenVersion + 1 "
            + "where u.email = :email and u.emailVerified = false")
    int takeOver(String email, String name, String hash, Role role);

    /**
     * A provider vouched for the address of a user nobody proved: whoever set its password does not own the
     * address, so the password goes and the user becomes the provider's. 0 if it was proven in the meantime.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update User u set u.name = :name, u.passwordHash = :hash, u.passwordSet = false, u.emailVerified = true, "
            + "u.tokenVersion = u.tokenVersion + 1 where u.id = :id and u.emailVerified = false")
    int claim(Long id, String name, String hash);

    /** A confirmed change of email, only while the user still has the address the link was sent from. */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update User u set u.email = :to where u.id = :id and u.email = :from")
    int moveEmail(Long id, String from, String to);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<TokenVersion> findByTokenVersionGreaterThan(int version);
}
