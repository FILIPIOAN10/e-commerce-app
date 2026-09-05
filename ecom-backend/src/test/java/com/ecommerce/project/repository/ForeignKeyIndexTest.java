package com.ecommerce.project.repository;

import com.ecommerce.project.config.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every foreign key can be searched from the child side without a sequential
 * scan.
 *
 * <p>Neither JPA nor Postgres creates this index for you: a {@code @JoinColumn}
 * generates no index, and a {@code REFERENCES} clause indexes only the parent's
 * primary key. So the child column — the one you filter on when assembling or
 * deleting an account — is unindexed unless someone writes the migration. Seven
 * of them were, including {@code bundle_products.product_id}, which is
 * {@code ON DELETE CASCADE} and therefore scanned inside every product delete.
 *
 * <p>Asserted as an invariant over {@code pg_constraint} rather than as a list
 * of expected index names, because the failure this guards against is the next
 * {@code @JoinColumn} added without one. A named list would still pass.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
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

    @Autowired private EntityManager entityManager;

    @Test
    @Transactional
    @DisplayName("no foreign key is left without an index on its own column")
    @SuppressWarnings("unchecked")
    void everyForeignKeyIsIndexed() {
        List<String> uncovered = entityManager.createNativeQuery(UNCOVERED_FOREIGN_KEYS).getResultList();

        assertThat(uncovered)
                .as("these foreign keys force a sequential scan on the child table; "
                    + "add an index in a migration, leading with the FK column")
                .isEmpty();
    }
}
