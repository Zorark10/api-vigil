package com.project.api_monitor.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exercises EmailJsClient against a local HTTP server standing in for the
 * EmailJS REST API: wire format (credentials + dynamic recipient), success,
 * non-2xx rejections, the 429 retry, missing configuration and the
 * one-request-per-second pacing.
 */
class EmailJsClientHttpTest {

	private static HttpServer server;
	private static int port;

	private static final List<String> requestBodies = new CopyOnWriteArrayList<>();
	private static final List<Long> requestStartNanos = new CopyOnWriteArrayList<>();
	private static final AtomicInteger requestCount = new AtomicInteger();

	private static volatile IntSupplier statusSupplier = () -> 200;

	@BeforeAll
	static void startStandInServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		port = server.getAddress().getPort();
		server.createContext("/api/v1.0/email/send", exchange -> {
			byte[] body = exchange.getRequestBody().readAllBytes();
			requestBodies.add(new String(body, StandardCharsets.UTF_8));
			requestStartNanos.add(System.nanoTime());
			requestCount.incrementAndGet();
			byte[] response = "OK".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(statusSupplier.getAsInt(), response.length);
			exchange.getResponseBody().write(response);
			exchange.close();
		});
		server.start();
	}

	@AfterAll
	static void stopStandInServer() {
		server.stop(0);
	}

	@BeforeEach
	void resetStandIn() {
		requestBodies.clear();
		requestStartNanos.clear();
		requestCount.set(0);
		statusSupplier = () -> 200;
	}

	private EmailJsClient client(long minIntervalMs) {
		return new EmailJsClient("svc_test", "tpl_test", "pub_test", "priv_test",
				"http://127.0.0.1:" + port + "/api/v1.0/email/send", minIntervalMs);
	}

	@Test
	void successSendsEmailJsPayloadWithCredentialsAndDynamicRecipient() {
		assertThat(client(0L).send("alice@example.com", "[api-monitor] DOWN: Billing API", "The monitor is down"))
				.isTrue();

		assertThat(requestCount.get()).isEqualTo(1);

		Map<String, Object> payload = JsonMapper.builder().build().readValue(requestBodies.get(0),
				new TypeReference<Map<String, Object>>() {});
		assertThat(payload)
				.containsEntry("service_id", "svc_test")
				.containsEntry("template_id", "tpl_test")
				.containsEntry("user_id", "pub_test")
				.containsEntry("accessToken", "priv_test");

		@SuppressWarnings("unchecked")
		Map<String, Object> templateParams = (Map<String, Object>) payload.get("template_params");
		assertThat(templateParams)
				.containsEntry("to_email", "alice@example.com")
				.containsEntry("subject", "[api-monitor] DOWN: Billing API")
				.containsEntry("message", "The monitor is down");
	}

	@Test
	void serverErrorReturnsFalseAndStopsAfterOneAttempt() {
		statusSupplier = () -> 500;

		assertThat(client(0L).send("alice@example.com", "s", "text")).isFalse();

		assertThat(requestCount.get()).isEqualTo(1);
	}

	@Test
	void rateLimitTriggersSingleRetryThatSucceeds() {
		AtomicInteger statusCalls = new AtomicInteger();
		statusSupplier = () -> statusCalls.getAndIncrement() == 0 ? 429 : 200;

		assertThat(client(0L).send("alice@example.com", "s", "text")).isTrue();

		assertThat(requestCount.get()).isEqualTo(2);
	}

	@Test
	void missingConfigurationFailsWithoutContactingEmailJs() {
		EmailJsClient unconfigured = new EmailJsClient("", "tpl_test", "pub_test", "priv_test",
				"http://127.0.0.1:" + port + "/api/v1.0/email/send", 0L);
		EmailJsClient noPrivateKey = new EmailJsClient("svc_test", "tpl_test", "pub_test", "",
				"http://127.0.0.1:" + port + "/api/v1.0/email/send", 0L);

		assertThat(unconfigured.send("alice@example.com", "s", "text")).isFalse();
		assertThat(noPrivateKey.send("alice@example.com", "s", "text")).isFalse();

		assertThat(requestCount.get()).isZero();
	}

	@Test
	void consecutiveSendsHonourOneRequestPerSecondPacing() {
		long minIntervalMs = 1200;
		EmailJsClient client = client(minIntervalMs);

		assertThat(client.send("alice@example.com", "s", "text")).isTrue();
		assertThat(client.send("bob@example.com", "s", "text")).isTrue();

		assertThat(requestCount.get()).isEqualTo(2);
		long gapNanos = requestStartNanos.get(1) - requestStartNanos.get(0);
		assertThat(gapNanos).isGreaterThanOrEqualTo(1_000_000_000L);
	}
}
