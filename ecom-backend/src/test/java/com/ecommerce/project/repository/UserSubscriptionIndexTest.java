package com.ecommerce.project.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code user_subscriptions} can be searched by every column it is actually
 * searched by, and cannot hold two rows for one Stripe subscription.
 *
 * <p>V8 created the table with no index and V28 added only the {@code plan_id}
 * foreign key, so the three lookups that matter were sequential scans — the
 * worst of them on the recurring-webhook path, which Stripe abandons after 20
 * seconds and then redelivers into the same handler. {@link ForeignKeyIndexTest}
 * did not catch it because none of the three is a foreign key.
 *
 * <p>Asserted here as named indexes rather than as an invariant, because the
 * invariant — "every column a derived finder queries" — is not visible to SQL;
 * it lives in {@code UserSubscriptionRepository}'s method names. The uniqueness
 * half is asserted by inserting, which is the part that would otherwise only be
 * discovered by a webhook throwing IncorrectResultSizeDataAccessException.
 *
 * <p>Runs the migrations against its own container for the same reason
 * {@link ForeignKeyIndexTest} does: the shared test context builds its schema
 * with {@code ddl-auto=create-drop} and no Flyway, so it contains none of these
 * indexes and could not answer the question.
 */
@DisplayName("user_subscriptions lookup indexes")
class UserSubscriptionIndexTest {

    private static PostgreSQLContainer<?> postgres;

    @BeforeAll
    static void startAndMigrate() {
        postgres = new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16"))
                .withDatabaseName("subscription_index_check")
                .withUsername("test")
                .withPassword("test");
        postgres.start();

        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @AfterAll
    static void stop() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    @DisplayName("every column the repository looks a subscription up by leads an index")
    void lookupColumnsAreIndexed() throws Exception {
        List<String> leadingColumns = new ArrayList<>();
        String query = """
                SELECT a.attname
                  FROM pg_index i
                  JOIN pg_attribute a
                    ON a.attrelid = i.indrelid AND a.attnum = i.indkey[0]
                 WHERE i.indrelid = 'user_subscriptions'::regclass
                """;
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(query)) {
            while (rows.next()) {
                leadingColumns.add(rows.getString(1));
            }
        }

        assertThat(leadingColumns)
                .as("these are findByStripeSubscriptionId (every recurring webhook), "
                    + "findByStripeCheckoutSessionId (every new subscription) and "
                    + "findByEmailOrderByCreatedAtDesc (the account page and the GDPR export)")
                .contains("stripe_subscription_id", "stripe_checkout_session_id", "email");
    }

    @Test
    @DisplayName("two rows cannot share one Stripe subscription id")
    void stripeSubscriptionIdIsUnique() throws Exception {
        long planId = insertPlan();

        assertThatCode(() -> insertSubscription(planId, "sub_duplicate", "cs_first"))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> insertSubscription(planId, "sub_duplicate", "cs_second"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("uk_user_subscriptions_stripe_id");
    }

    @Test
    @DisplayName("checkouts that never reached Stripe stay outside the unique index")
    void nullStripeIdsDoNotCollide() throws Exception {
        long planId = insertPlan();

        // A started-but-abandoned checkout leaves both ids NULL. A plain UNIQUE
        // would tolerate these too, but the index is partial so they are not
        // carried at all — which is most of the rows on a real install.
        assertThatCode(() -> {
            insertSubscription(planId, null, null);
            insertSubscription(planId, null, null);
        }).doesNotThrowAnyException();
    }

    private long insertPlan() throws SQLException {
        try (Connection connection = connect();
             Statement statement = connection.createStatement()) {
            // "interval" is a reserved word, hence the quoting.
            try (ResultSet keys = statement.executeQuery(
                    "INSERT INTO subscription_plans (name, \"interval\", amount, currency) "
                    + "VALUES ('Index test plan', 'month', 10, 'USD') RETURNING plan_id")) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private void insertSubscription(long planId, String subscriptionId, String checkoutSessionId)
            throws SQLException {
        try (Connection connection = connect();
             var insert = connection.prepareStatement(
                     "INSERT INTO user_subscriptions "
                     + "(email, plan_id, status, stripe_subscription_id, stripe_checkout_session_id) "
                     + "VALUES (?, ?, 'ACTIVE', ?, ?)")) {
            insert.setString(1, "index-test@example.com");
            insert.setLong(2, planId);
            insert.setString(3, subscriptionId);
            insert.setString(4, checkoutSessionId);
            insert.executeUpdate();
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
