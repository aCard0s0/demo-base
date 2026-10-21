package com.demo.todoservice;

import com.demo.auth.client.Caller;
import com.demo.todoservice.todo.TodoService;
import com.demo.todoservice.todo.entities.Todo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The real database, in a throwaway container: the Liquibase changeset builds the schema and Hibernate validates
 * the entity against it (the context would not start otherwise). Skipped, not failed, without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class PostgresIntegrationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    TodoService todos;

    @Autowired
    JdbcClient db;

    @Test
    void theChangesetsRan() {
        List<String> applied = db.sql("SELECT id FROM databasechangelog WHERE exectype = 'EXECUTED' ORDER BY orderexecuted").query(String.class).list();
        assertEquals(List.of("1-init"), applied);
    }

    @Test
    void aTodoRoundTripsThroughTheMigratedTable() {
        Caller owner = new Caller("1", "USER");
        Todo saved = todos.add(owner, "buy milk");
        assertTrue(todos.toggle(owner, saved.getId()).isDone());
        assertEquals(List.of("buy milk"), todos.list(owner).stream().map(Todo::getTitle).toList());
    }
}
