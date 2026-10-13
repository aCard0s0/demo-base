package com.demo.authservice;

import com.demo.authservice.user.UserService;
import com.demo.authservice.user.entities.Role;
import com.demo.authservice.user.entities.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The real database, in a throwaway container: the Liquibase changesets build the schema, Hibernate validates the
 * entities against it (the context would not start otherwise), and what the SQLite tests cannot vouch for -- the
 * column defaults, the unique email, the agent-version upsert -- runs against Postgres. Skipped, not failed, without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@Import(RecordingMail.class)
class PostgresIntegrationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    UserService users;

    @Autowired
    JdbcClient db;

    @Test
    void theChangesetsRan() {
        List<String> applied = db.sql("SELECT id FROM databasechangelog WHERE exectype = 'EXECUTED' ORDER BY orderexecuted").query(String.class).list();
        assertEquals(List.of("1-init", "2-password-set", "3-email-verified"), applied);
    }

    @Test
    void aRowWrittenOutsideHibernateGetsTheColumnDefaultsAndEmailStaysUnique() {
        db.sql("INSERT INTO users (name, email, password_hash) VALUES ('raw', 'raw@example.com', 'x')").update();
        assertEquals(List.of("USER", "false", "0", "true", "true"), db.sql("SELECT role, suspended::text, token_version::text, password_set::text, email_verified::text FROM users WHERE email = 'raw@example.com'")
                .query((rs, n) -> List.of(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5))).single());
        assertThrows(DataIntegrityViolationException.class,
                () -> db.sql("INSERT INTO users (name, email, password_hash) VALUES ('again', 'raw@example.com', 'x')").update());
    }

    @Test
    void theServiceWorksOnTheMigratedSchema() {
        User u = users.register("Pat", "pat@example.com", "a-long-enough-password");
        assertEquals(Role.USER, u.getRole());
        users.revokeAgentTokens(42L);
        users.revokeAgentTokens(42L);
        assertEquals(2, db.sql("SELECT version FROM agent_token_versions WHERE agent_id = 42").query(Integer.class).single(),
                "the upsert inserts at 1 and adds 1 after");
    }
}
