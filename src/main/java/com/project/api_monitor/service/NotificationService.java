package com.project.api_monitor.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.project.api_monitor.exception.DuplicateRecipientException;
import com.project.api_monitor.exception.RecipientNotFoundException;
import com.project.api_monitor.model.IncidentEvent;
import com.project.api_monitor.model.IncidentStatus;
import com.project.api_monitor.model.NotificationRecipient;
import com.project.api_monitor.model.NotificationSettings;
import com.project.api_monitor.model.TestEmailResult;
import com.project.api_monitor.repository.NotificationRecipientRepo;
import com.project.api_monitor.repository.NotificationSettingsRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Email notification management and delivery: recipients and the global
 * on/off switch live in PostgreSQL; incident DOWN/recovery emails are driven
 * by the incident-events Kafka stream, which fires exactly once per incident
 * transition, so no address ever receives repeated mails for consecutive
 * failed checks of the same outage.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

	private static final DateTimeFormatter STAMP =
			DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss", Locale.ENGLISH);

	private final NotificationRecipientRepo recipientRepo;
	private final NotificationSettingsRepo settingsRepo;
	private final EmailDeliveryService emailDeliveryService;

	// ---- settings ----

	public NotificationSettings getSettings() {
		return settingsRepo.findAll().stream()
				.min(java.util.Comparator.comparing(NotificationSettings::getId))
				.orElseGet(() -> settingsRepo.save(
						NotificationSettings.builder().emailEnabled(true).build()));
	}

	public void setEmailEnabled(boolean enabled) {
		NotificationSettings settings = getSettings();
		settings.setEmailEnabled(enabled);
		settingsRepo.save(settings);
	}

	// ---- recipients ----

	public List<NotificationRecipient> listRecipients() {
		return recipientRepo.findAllByOrderByIdAsc();
	}

	public NotificationRecipient addRecipient(String email) {
		String normalized = email.trim().toLowerCase(Locale.ROOT);
		if (recipientRepo.existsByEmail(normalized)) {
			throw new DuplicateRecipientException();
		}
		try {
			return recipientRepo.save(NotificationRecipient.builder()
					.email(normalized)
					.enabled(true)
					.createdAt(LocalDateTime.now())
					.build());
		} catch (DataIntegrityViolationException ex) {
			// Concurrent add of the same address slipped past the exists check.
			throw new DuplicateRecipientException();
		}
	}

	public void removeRecipient(Integer id) {
		if (!recipientRepo.existsById(id)) {
			throw new RecipientNotFoundException(id);
		}
		recipientRepo.deleteById(id);
	}

	public NotificationRecipient setRecipientEnabled(Integer id, boolean enabled) {
		NotificationRecipient recipient = recipientRepo.findById(id)
				.orElseThrow(() -> new RecipientNotFoundException(id));
		recipient.setEnabled(enabled);
		return recipientRepo.save(recipient);
	}

	// ---- email delivery ----

	public TestEmailResult sendTestEmail() {
		String subject = "[api-monitor] Test notification";
		String text = "This is a test email from your api-monitor instance.\n"
				+ "If you received it, incident notifications for it will arrive at this address.";
		return sendToEnabledRecipients(subject, text);
	}

	/**
	 * Called by the incident-events Kafka consumer exactly once per incident
	 * transition (DOWN / RECOVERED). Never throws: notification problems are
	 * logged and leave monitoring, Kafka processing and incidents untouched.
	 */
	public void handleIncidentEvent(IncidentEvent event) {
		try {
			if (!getSettings().isEmailEnabled()) {
				log.debug("Email notifications disabled; skipping incident event {}", event.getIncidentId());
				return;
			}
			boolean down = event.getStatus() == IncidentStatus.OPEN;
			String monitorName = event.getMonitorName() != null ? event.getMonitorName() : "Monitor " + event.getMonitorId();
			String subject = down
					? "[api-monitor] DOWN: " + monitorName
					: "[api-monitor] RECOVERED: " + monitorName;
			String text = down ? buildDownText(event, monitorName) : buildRecoveredText(event, monitorName);
			sendToEnabledRecipients(subject, text);
		} catch (Exception ex) {
			log.error("Incident notification for incident {} failed", event != null ? event.getIncidentId() : null, ex);
		}
	}

	private TestEmailResult sendToEnabledRecipients(String subject, String text) {
		int succeeded = 0;
		int failed = 0;
		for (NotificationRecipient recipient : recipientRepo.findAllByOrderByIdAsc()) {
			if (!recipient.isEnabled()) {
				continue;
			}
			if (emailDeliveryService.send(recipient.getEmail(), subject, text)) {
				succeeded++;
			} else {
				failed++;
			}
		}
		return new TestEmailResult(succeeded, failed);
	}

	private String buildDownText(IncidentEvent event, String monitorName) {
		return "An api-monitor check failed and an incident was opened.\n\n"
				+ "Monitor: " + monitorName + "\n"
				+ "URL: " + event.getMonitorUrl() + "\n"
				+ "Incident: #" + event.getIncidentId() + "\n"
				+ "Status: DOWN\n"
				+ "Started at: " + event.getStartedAt().format(STAMP) + "\n\n"
				+ "No further DOWN emails will be sent for this incident until it recovers.";
	}

	private String buildRecoveredText(IncidentEvent event, String monitorName) {
		return "A monitor incident recovered.\n\n"
				+ "Monitor: " + monitorName + "\n"
				+ "URL: " + event.getMonitorUrl() + "\n"
				+ "Incident: #" + event.getIncidentId() + "\n"
				+ "Status: RECOVERED\n"
				+ "Started at: " + event.getStartedAt().format(STAMP) + "\n"
				+ "Resolved at: " + event.getResolvedAt().format(STAMP);
	}
}
