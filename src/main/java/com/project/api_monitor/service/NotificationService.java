package com.project.api_monitor.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.api_monitor.exception.DuplicateRecipientException;
import com.project.api_monitor.exception.RecipientNotFoundException;
import com.project.api_monitor.model.AppUser;
import com.project.api_monitor.model.IncidentEvent;
import com.project.api_monitor.model.IncidentStatus;
import com.project.api_monitor.model.Monitor;
import com.project.api_monitor.model.NotificationRecipient;
import com.project.api_monitor.model.NotificationSettings;
import com.project.api_monitor.model.TestEmailResult;
import com.project.api_monitor.repository.MonitorRepo;
import com.project.api_monitor.repository.NotificationRecipientRepo;
import com.project.api_monitor.repository.NotificationSettingsRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Email notification management and delivery: recipients and the per-account
 * on/off switch live in PostgreSQL; incident DOWN/recovery emails are driven
 * by the incident-events Kafka stream, which fires exactly once per incident
 * transition, so no address ever receives repeated mails for consecutive
 * failed checks of the same outage. Everything is scoped to the owning
 * account; the incident consumer resolves the owner of the incident's monitor
 * from the database, so background delivery never depends on HTTP context.
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
	private final MonitorRepo monitorRepo;

	// ---- settings ----

	public NotificationSettings getSettings(AppUser owner) {
		return settingsRepo.findByOwnerId(owner.getId())
				.orElseGet(() -> settingsRepo.save(
						NotificationSettings.builder().owner(owner).emailEnabled(true).build()));
	}

	public void setEmailEnabled(AppUser owner, boolean enabled) {
		NotificationSettings settings = getSettings(owner);
		settings.setEmailEnabled(enabled);
		settingsRepo.save(settings);
	}

	// ---- recipients ----

	public List<NotificationRecipient> listRecipients(AppUser owner) {
		return recipientRepo.findAllByOwnerIdOrderByIdAsc(owner.getId());
	}

	public NotificationRecipient addRecipient(String email, AppUser owner) {
		String normalized = email.trim().toLowerCase(Locale.ROOT);
		if (recipientRepo.existsByOwnerIdAndEmail(owner.getId(), normalized)) {
			throw new DuplicateRecipientException();
		}
		try {
			return recipientRepo.save(NotificationRecipient.builder()
					.owner(owner)
					.email(normalized)
					.enabled(true)
					.createdAt(LocalDateTime.now())
					.build());
		} catch (DataIntegrityViolationException ex) {
			// Concurrent add of the same address slipped past the exists check.
			throw new DuplicateRecipientException();
		}
	}

	public void removeRecipient(Integer id, AppUser owner) {
		if (!recipientRepo.existsByIdAndOwnerId(id, owner.getId())) {
			throw new RecipientNotFoundException(id);
		}
		recipientRepo.deleteById(id);
	}

	public NotificationRecipient setRecipientEnabled(Integer id, boolean enabled, AppUser owner) {
		NotificationRecipient recipient = recipientRepo.findByIdAndOwnerId(id, owner.getId())
				.orElseThrow(() -> new RecipientNotFoundException(id));
		recipient.setEnabled(enabled);
		return recipientRepo.save(recipient);
	}

	// ---- email delivery ----

	public TestEmailResult sendTestEmail(AppUser owner) {
		String subject = "[api-monitor] Test notification";
		String text = "This is a test email from your api-monitor instance.\n"
				+ "If you received it, incident notifications for it will arrive at this address.";
		return sendToEnabledRecipients(owner, subject, text);
	}

	/**
	 * Called by the incident-events Kafka consumer exactly once per incident
	 * transition (DOWN / RECOVERED). The recipient list and the on/off switch
	 * belong to the account owning the incident's monitor, resolved from the
	 * database — no HTTP authentication context exists in the consumer. Never
	 * throws: notification problems are logged and leave monitoring, Kafka
	 * processing and incidents untouched.
	 */
	@Transactional(readOnly = true)
	public void handleIncidentEvent(IncidentEvent event) {
		try {
			AppUser owner = monitorRepo.findById(event.getMonitorId())
					.map(Monitor::getOwner)
					.orElse(null);
			if (owner == null) {
				// The monitor was deleted between producing and consuming the
				// event; there is no workspace left to notify.
				log.debug("No monitor {} for incident event {}; skipping notification", event.getMonitorId(), event.getIncidentId());
				return;
			}
			if (!getSettings(owner).isEmailEnabled()) {
				log.debug("Email notifications disabled for {}; skipping incident event {}", owner.getUsername(), event.getIncidentId());
				return;
			}
			boolean down = event.getStatus() == IncidentStatus.OPEN;
			String monitorName = event.getMonitorName() != null ? event.getMonitorName() : "Monitor " + event.getMonitorId();
			String subject = down
					? "[api-monitor] DOWN: " + monitorName
					: "[api-monitor] RECOVERED: " + monitorName;
			String text = down ? buildDownText(event, monitorName) : buildRecoveredText(event, monitorName);
			sendToEnabledRecipients(owner, subject, text);
		} catch (Exception ex) {
			log.error("Incident notification for incident {} failed", event != null ? event.getIncidentId() : null, ex);
		}
	}

	private TestEmailResult sendToEnabledRecipients(AppUser owner, String subject, String text) {
		int succeeded = 0;
		int failed = 0;
		for (NotificationRecipient recipient : recipientRepo.findAllByOwnerIdOrderByIdAsc(owner.getId())) {
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
