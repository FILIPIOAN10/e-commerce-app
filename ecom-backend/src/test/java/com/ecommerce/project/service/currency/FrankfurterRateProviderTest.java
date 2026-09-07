package com.ecommerce.project.service.currency;

import com.ecommerce.project.config.CurrencyProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards the reason {@link FrankfurterRateProvider} configures HTTP timeouts: a
 * slow upstream must make {@code ratesFor} throw, because the registry's
 * fall-through to the fixed table is driven by an exception, not a clock.
 */
class FrankfurterRateProviderTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private FrankfurterRateProvider providerFor(String baseUrl, Duration readTimeout) {
        CurrencyProperties properties = new CurrencyProperties();
        properties.getFrankfurter().setBaseUrl(baseUrl);
        properties.getFrankfurter().setConnectTimeout(Duration.ofMillis(500));
        properties.getFrankfurter().setReadTimeout(readTimeout);
        return new FrankfurterRateProvider(properties);
    }

    private String startServer(long handlerDelayMillis, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/latest", exchange -> {
            try {
                Thread.sleep(handlerDelayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort();
    }

    @Test
    void throwsWhenTheUpstreamStallsPastTheReadTimeout() throws IOException {
        String baseUrl = startServer(2_000, "{\"base\":\"USD\",\"rates\":{\"EUR\":0.9}}");
        FrankfurterRateProvider provider = providerFor(baseUrl, Duration.ofMillis(150));

        assertThatThrownBy(() -> provider.ratesFor("USD"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void parsesRatesOnTheHappyPath() throws IOException {
        String baseUrl = startServer(0, "{\"base\":\"USD\",\"date\":\"2026-09-07\",\"rates\":{\"eur\":0.9,\"gbp\":0.79}}");
        FrankfurterRateProvider provider = providerFor(baseUrl, Duration.ofSeconds(3));

        var rates = provider.ratesFor("USD");

        assertThat(rates.get("USD")).isEqualByComparingTo("1");
        assertThat(rates.get("EUR")).isEqualByComparingTo("0.9");
        assertThat(rates.get("GBP")).isEqualByComparingTo("0.79");
    }
}
