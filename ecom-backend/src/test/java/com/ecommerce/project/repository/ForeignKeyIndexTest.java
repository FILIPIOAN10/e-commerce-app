package com.ecommerce.project.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every foreign key can be searched from the child side without a sequential
 * scan.
 *
 * <p>Neither JPA nor Postgres creates that index for you: a {@code @JoinColumn}
 * generates nothing, and a {@code REFERENCES} clause indexes only the parent's
 * primary key. So the child column — the one filtered on when assembling or
 * deleting an account — is unindexed unless a migration says otherwise. Seven
 * were, including {@code bundle_products.product_id}, which is
 * {@code ON DELETE CASCADE} and therefore scanned inside every product delete.
 *
 * <p>Asserted as an invariant over {@code pg_constraint} rather than as a list
 * of expected index names, because what this guards against is the next
 * {@code @JoinColumn} added without one — a named list would still pass.
 *
 * <p>Deliberately does not use the shared Spring test context. That context
 * builds its schema with {@code ddl-auto=create-drop} and
 * {@code flyway.enabled=false}, so it contains no migration index at all and
 * every foreign key in it looks uncovered. The question here is what
 * <em>production</em> gets, which means running the migrations and looking at
 * the result.
 */
@DisplayName("Foreign key index coverage")
class ForeignKeyIndexTest {

    /**
     * Foreign keys whose leading column starts no index. A composite index
     * counts only for its first column: {@code (user_id, product_id)} serves a
     * search by user and does nothing for a search by product.
     */
    private static final String UNCOVERED_FOREIGN_KEYS = """
            SELECT con.conrelid::regclass::text || '.' || att.attname
              FROM pg_constraint con
              JOIN pg_attribute att
                ON att.attrelid = con.conrelid AND att.attnum = con.conkey[1]
             WHERE con.contype = 'f'
               AND NOT EXISTS (SELECT 1 FROM pg_index idx
                                WHERE idx.indrelid = con.conrelid
                                  AND idx.indkey[0] = con.conkey[1])
             ORDER BY 1
            """;

    @Test
    @DisplayName("no foreign key is left without an index on its own column")
    void everyForeignKeyIsIndexed() throws Exception {
        try (PostgreSQLContainer<?> postgres =
                     new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16"))
                             .withDatabaseName("fk_index_check")
                             .withUsername("test")
                             .withPassword("test")) {
            postgres.start();

            Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            List<String> uncovered = new ArrayList<>();
            try (Connection connection = DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery(UNCOVERED_FOREIGN_KEYS)) {
                while (rows.next()) {
                    uncovered.add(rows.getString(1));
                }
            }

            assertThat(uncovered)
                    .as("these foreign keys force a sequential scan on the child table; "
                        + "add an index in a migration, leading with the FK column")
                    .isEmpty();
        }
    }
}
