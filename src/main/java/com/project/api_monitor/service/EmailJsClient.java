package com.project.api_monitor.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sends notification emails through the EmailJS REST API
 * (POST https://api.emailjs.com/api/v1.0/email/send).
 *
 * All EmailJS credentials stay on the backend (service id, template id,
 * public key as user_id, private key as accessToken) - dashboard users only
 * ever enter recipient addresses. The recipient is passed dynamically through
 * the template's To Email variable (template_params.to_email).
 *
 * EmailJS allows one REST request per second, so sends are strictly sequential
 * and spaced with a minimum interval; a rejected 429 response is retried once
 * after that spacing. Requests are bounded by timeouts so a slow EmailJS can
 * never stall the incident-events consumer.
 *
 * Any failure is logged and reported to the caller (false) instead of thrown,
 * so an EmailJS outage can never interrupt monitoring, Kafka processing or
 * incident creation.
 */
@Service
@Slf4j
public class EmailJsClient {

	private static final String DEFAULT_API_URL = "https://api.emailjs.com/api/v1.0/email/send";

	private final String serviceId;
	private final String templateId;
	private final String publicKey;
	private final String privateKey;
	private final String apiUrl;
	private final long minIntervalMs;
	private final HttpClient http;
	private final JsonMapper jsonMapper;

	/** Monotonic nano timestamp when the next send slot is free (pacing). */
	private long nextAllowedAtNanos = 0L;

	@Autowired
	public EmailJsClient(
			@Value("${app.emailjs.service-id:}") String serviceId,
			@Value("${app.emailjs.template-id:}") String templateId,
			@Value("${app.emailjs.public-key:}") String publicKey,
			@Value("${app.emailjs.private-key:}") String privateKey,
			@Value("${app.emailjs.min-interval-ms:1100}") long minIntervalMs) {
		this(serviceId, templateId, publicKey, privateKey, DEFAULT_API_URL, minIntervalMs);
	}

	EmailJsClient(String serviceId, String templateId, String publicKey, String privateKey,
			String apiUrl, long minIntervalMs) {
		this.serviceId = orBlank(serviceId);
		this.templateId = orBlank(templateId);
		this.publicKey = orBlank(publicKey);
		this.privateKey = orBlank(privateKey);
		this.apiUrl = apiUrl;
		this.minIntervalMs = Math.max(0, minIntervalMs);
		this.jsonMapper = JsonMapper.builder().build();
		this.http = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.build();
	}

	private static String orBlank(String value) {
		return value == null ? "" : value.trim();
	}

	/**
	 * Sends one message to one recipient. Returns true when EmailJS accepted
	 * the request.
	 */
	public boolean send(String to, String subject, String text) {
		if (serviceId.isBlank() || templateId.isBlank() || publicKey.isBlank() || privateKey.isBlank()) {
			log.error("EmailJS is not configured; set EMAILJS_SERVICE_ID, EMAILJS_TEMPLATE_ID, "
					+ "EMAILJS_PUBLIC_KEY and EMAILJS_PRIVATE_KEY. Notification to {} was not sent.", to);
			return false;
		}
		if (!acquireSlot()) {
			return false;
		}
		try {
			int status = post(to, subject, text);
			if (status >= 200 && status < 300) {
				return true;
			}
			if (status == 429) {
				// Rate limited: the spacing slot already guarantees one second
				// since the previous request, so a single retry is safe.
				log.warn("EmailJS rate limit hit (429); retrying once for {}", to);
				return acquireSlot() && isSuccessful(post(to, subject, text));
			}
			return false;
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			log.error("Notification email delivery to {} was interrupted", to);
			return false;
		} catch (IOException ex) {
			log.error("Notification email delivery to {} failed: {}", to, ex.toString());
			return false;
		}
	}

	private boolean isSuccessful(int status) {
		return status >= 200 && status < 300;
	}

	private int post(String to, String subject, String text) throws IOException, InterruptedException {
		Map<String, Object> templateParams = new LinkedHashMap<>();
		templateParams.put("to_email", to);
		templateParams.put("subject", subject);
		templateParams.put("message", text);

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("service_id", serviceId);
		payload.put("template_id", templateId);
		payload.put("user_id", publicKey);
		payload.put("accessToken", privateKey);
		payload.put("template_params", templateParams);

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(apiUrl))
				.header("Content-Type", "application/json")
				.timeout(Duration.ofSeconds(15))
				.POST(HttpRequest.BodyPublishers.ofByteArray(jsonMapper.writeValueAsBytes(payload)))
				.build();
		HttpResponse<String> response =
				http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		int status = response.statusCode();
		if (!isSuccessful(status)) {
			log.error("EmailJS rejected notification for {}: HTTP {} {}", to, status, truncate(response.body()));
		}
		return status;
	}

	private String truncate(String body) {
		if (body == null) {
			return "(empty)";
		}
		return body.length() > 200 ? body.substring(0, 200) + "..." : body;
	}

	private synchronized boolean acquireSlot() {
		long waitNanos = nextAllowedAtNanos - System.nanoTime();
		if (waitNanos > 0) {
			try {
				TimeUnit.NANOSECONDS.sleep(waitNanos);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		nextAllowedAtNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(minIntervalMs);
		return true;
	}
}
