package com.example.sagatest;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
public class OrderSagaIntegrationTest {

    private static final int ORDER_SERVICE_PORT = 8080;
    private static final String ORDER_SERVICE_NAME = "order-service_1";

    @Container
    public static DockerComposeContainer<?> environment =
            new DockerComposeContainer<>(new File("../docker-compose.yml"))
                    .withExposedService(ORDER_SERVICE_NAME, ORDER_SERVICE_PORT,
                            Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(240)))
                    .withLocalCompose(true);

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = "http://" + environment.getServiceHost(ORDER_SERVICE_NAME, ORDER_SERVICE_PORT);
        RestAssured.port = environment.getServicePort(ORDER_SERVICE_NAME, ORDER_SERVICE_PORT);
    }

    @Test
    void shouldCompleteOrderSagaSuccessfully() throws Exception {
        // --- Setup Data ---
        // Pre-populate inventory with a product
        try (Connection conn = getDbConnection("inventory-db", 5432, "inventory_user", "inventory_password", "inventory_db");
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM inventory;"); // Clean up previous state
            stmt.execute("INSERT INTO inventory (product_id, stock) VALUES ('product123', 10);");
        }

        // --- Trigger Saga ---
        String orderRequest = "{\"customerId\": \"customer_abc\", \"productId\": \"product123\", \"quantity\": 2, \"price\": 50.0}";

        UUID orderId = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(orderRequest)
                .when()
                .post("/api/orders")
                .then()
                .statusCode(201)
                .extract()
                .body()
                .as(UUID.class);

        // --- Assert Final State ---
        // Use Awaitility to wait for the order status to become COMPLETED
        await().atMost(30, SECONDS).untilAsserted(() -> {
            try (Connection conn = getDbConnection("order-db", 5432, "order_user", "order_password", "order_db");
                 Statement stmt = conn.createStatement()) {

                ResultSet rs = stmt.executeQuery("SELECT status FROM orders WHERE id = '" + orderId + "'");
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("status")).isEqualTo("COMPLETED");
            }
        });

        // Verify payment service state
        try (Connection conn = getDbConnection("payment-db", 5432, "payment_user", "payment_password", "payment_db");
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT status FROM payments WHERE order_id = '" + orderId + "'");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("status")).isEqualTo("SUCCESSFUL");
        }

        // Verify inventory service state
        try (Connection conn = getDbConnection("inventory-db", 5432, "inventory_user", "inventory_password", "inventory_db");
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT stock FROM inventory WHERE product_id = 'product123'");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("stock")).isEqualTo(8); // 10 - 2
        }

        // Verify shipping service state
        try (Connection conn = getDbConnection("shipping-db", 5432, "shipping_user", "shipping_password", "shipping_db");
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT status FROM shipments WHERE order_id = '" + orderId + "'");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("status")).isEqualTo("PENDING");
        }
    }

    private Connection getDbConnection(String host, int port, String user, String password, String dbName) throws Exception {
        // We need to get the mapped port for the database container
        String dbHost = environment.getServiceHost(host, port);
        int dbPort = environment.getServicePort(host, port);
        String url = String.format("jdbc:postgresql://%s:%d/%s", dbHost, dbPort, dbName);
        return DriverManager.getConnection(url, user, password);
    }
} 