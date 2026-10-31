package com.project.api_monitor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.project.api_monitor.model.Incident;
import com.project.api_monitor.model.IncidentEvent;
import com.project.api_monitor.model.IncidentStatus;
import com.project.api_monitor.model.Monitor;
import com.project.api_monitor.model.MonitorResult;
import com.project.api_monitor.model.MonitorStatus;
import com.project.api_monitor.model.NotificationRecipient;
import com.project.api_monitor.repository.IncidentRepository;
import com.project.api_monitor.repository.MonitorRepo;
import com.project.api_monitor.repository.NotificationRecipientRepo;
import com.project.api_monitor.repository.NotificationSettingsRepo;

@SpringBootTest
@ActiveProfiles("test")
class IncidentNotificationTest {

	private static final DateTimeFormatter STAMP =
			DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss", Locale.ENGLISH);

	@Autowired
	private MonitorRepo monitorRepo;

	@Autowired
	private IncidentRepository incidentRepo;

	@Autowired
	private NotificationRecipientRepo recipientRepo;

	@Autowired
	private NotificationSettingsRepo settingsRepo;

	@Autowired
	private IncidentService incidentService;

	@Autowired
	private NotificationService notificationService;

	@MockitoBean
	private IncidentEventProducer incidentEventProducer;

	@MockitoBean
	private EmailJsClient emailJsClient;

	private Monitor monitor;

	@BeforeEach
	void setUp() {
		incidentRepo.deleteAll();
		monitorRepo.deleteAll();
		recipientRepo.deleteAll();
		settingsRepo.deleteAll();

		monitor = monitorRepo.save(Monitor.builder()
				.name("Billing API")
				.url("https://billing.example.com/health")
				.interval(60)
				.timeout(5)
				.enabled(true)
				.createdAt(LocalDateTime.now())
				.httpMethod(com.project.api_monitor.model.HttpMethod.GET)
				.build());
	}

	private MonitorResult result(MonitorStatus status, LocalDateTime checkedAt) {
		return MonitorResult.builder()
				.monitor(monitor)
				.status(status)
				.statusCode(200)
				.responseTime(100L)
				.checkedAt(checkedAt)
				.build();
	}

	private NotificationRecipient recipient(String email, boolean enabled) {
		return recipientRepo.save(NotificationRecipient.builder()
				.email(email)
				.enabled(enabled)
				.createdAt(LocalDateTime.now())
				.build());
	}

	@Test
	void downChecksOpenOneIncidentAndProduceExactlyOneEvent() {
		incidentService.processResult(result(MonitorStatus.DOWN, LocalDateTime.of(2026, 1, 1, 9, 0)));
		incidentService.processResult(result(MonitorStatus.DOWN, LocalDateTime.of(2026, 1, 1, 9, 1)));
		incidentService.processResult(result(MonitorStatus.DOWN, LocalDateTime.of(2026, 1, 1, 9, 2)));

		ArgumentCaptor<IncidentEvent> events = ArgumentCaptor.forClass(IncidentEvent.class);
		verify(incidentEventProducer, times(1)).sendIncidentEvent(events.capture());

		IncidentEvent event = events.getValue();
		assertThat(event.getStatus()).isEqualTo(IncidentStatus.OPEN);
		assertThat(event.getMonitorId()).isEqualTo(monitor.getId());
		assertThat(event.getMonitorName()).isEqualTo("Billing API");
		assertThat(event.getMonitorUrl()).isEqualTo("https://billing.example.com/health");

		List<Incident> incidents = incidentRepo.findAll();
		assertThat(incidents).hasSize(1);
		assertThat(incidents.get(0).getStatus()).isEqualTo(IncidentStatus.OPEN);
	}

	@Test
	void recoveryResolvesIncidentAndProducesExactlyOneResolvedEvent() {
		incidentService.processResult(result(MonitorStatus.DOWN, LocalDateTime.of(2026, 1, 1, 9, 0)));
		incidentService.processResult(result(MonitorStatus.UP, LocalDateTime.of(2026, 1, 1, 9, 5)));
		incidentService.processResult(result(MonitorStatus.UP, LocalDateTime.of(2026, 1, 1, 9, 6)));

		List<Incident> incidents = incidentRepo.findAll();
		assertThat(incidents).hasSize(1);
		assertThat(incidents.get(0).getStatus()).isEqualTo(IncidentStatus.RESOLVED);
		assertThat(incidents.get(0).getResolvedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 9, 5));

		ArgumentCaptor<IncidentEvent> events = ArgumentCaptor.forClass(IncidentEvent.class);
		verify(incidentEventProducer, times(2)).sendIncidentEvent(events.capture());
		assertThat(events.getAllValues().get(0).getStatus()).isEqualTo(IncidentStatus.OPEN);
		assertThat(events.getAllValues().get(1).getStatus()).isEqualTo(IncidentStatus.RESOLVED);
	}

	@Test
	void newOutageAfterRecoveryStartsNewIncidentWithOwnEvent() {
		incidentService.processResult(result(MonitorStatus.DOWN, LocalDateTime.of(2026, 1, 1, 9, 0)));
		incidentService.processResult(result(MonitorStatus.UP, LocalDateTime.of(2026, 1, 1, 9, 5)));
		incidentService.processResult(result(MonitorStatus.DOWN, LocalDateTime.of(2026, 1, 1, 10, 0)));

		assertThat(incidentRepo.count()).isEqualTo(2);
		ArgumentCaptor<IncidentEvent> events = ArgumentCaptor.forClass(IncidentEvent.class);
		verify(incidentEventProducer, times(3)).sendIncidentEvent(events.capture());
		assertThat(events.getAllValues()).extracting(IncidentEvent::getStatus)
				.containsExactly(IncidentStatus.OPEN, IncidentStatus.RESOLVED, IncidentStatus.OPEN);
	}

	@Test
	void downNotificationMailsEnabledRecipientsOnceWithDetails() {
		recipient("alice@example.com", true);
		recipient("bob@example.com", false);
		recipient("carol@example.com", true);

		LocalDateTime startedAt = LocalDateTime.of(2026, 1, 1, 9, 0);
		notificationService.handleIncidentEvent(IncidentEvent.builder()
				.monitorId(monitor.getId())
				.monitorName(monitor.getName())
				.monitorUrl(monitor.getUrl())
				.incidentId(42)
				.status(IncidentStatus.OPEN)
				.startedAt(startedAt)
				.resolvedAt(null)
				.build());

		ArgumentCaptor<String> recipients = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> subjects = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> bodies = ArgumentCaptor.forClass(String.class);
		verify(emailJsClient, times(2)).send(recipients.capture(), subjects.capture(), bodies.capture());

		assertThat(recipients.getAllValues())
				.containsExactlyInAnyOrder("alice@example.com", "carol@example.com");
		assertThat(subjects.getAllValues())
				.containsExactly("[api-monitor] DOWN: Billing API", "[api-monitor] DOWN: Billing API");
		assertThat(bodies.getAllValues()).allSatisfy(body -> {
			assertThat(body).contains(
					"https://billing.example.com/health", "Incident: #42", "DOWN",
					"Started at: " + startedAt.format(STAMP),
					"No further DOWN emails will be sent for this incident until it recovers.");
		});
	}

	@Test
	void recoveryNotificationMailMentionsResolvedTimestamp() {
		recipient("alice@example.com", true);

		LocalDateTime startedAt = LocalDateTime.of(2026, 1, 1, 9, 0);
		LocalDateTime resolvedAt = LocalDateTime.of(2026, 1, 1, 9, 5);
		notificationService.handleIncidentEvent(IncidentEvent.builder()
				.monitorId(monitor.getId())
				.monitorName(monitor.getName())
				.monitorUrl(monitor.getUrl())
				.incidentId(42)
				.status(IncidentStatus.RESOLVED)
				.startedAt(startedAt)
				.resolvedAt(resolvedAt)
				.build());

		ArgumentCaptor<String> recipients = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> subjects = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> bodies = ArgumentCaptor.forClass(String.class);
		verify(emailJsClient, times(1)).send(recipients.capture(), subjects.capture(), bodies.capture());

		assertThat(recipients.getValue()).isEqualTo("alice@example.com");
		assertThat(subjects.getValue()).isEqualTo("[api-monitor] RECOVERED: Billing API");
		assertThat(bodies.getValue())
				.contains("https://billing.example.com/health",
						"Started at: " + startedAt.format(STAMP),
						"Resolved at: " + resolvedAt.format(STAMP));
	}

	@Test
	void disabledEmailNotificationsSuppressMail() {
		recipient("alice@example.com", true);
		settingsRepo.save(com.project.api_monitor.model.NotificationSettings.builder().emailEnabled(false).build());

		notificationService.handleIncidentEvent(IncidentEvent.builder()
				.monitorId(monitor.getId()).monitorName(monitor.getName()).monitorUrl(monitor.getUrl())
				.incidentId(42).status(IncidentStatus.OPEN)
				.startedAt(LocalDateTime.now().minusMinutes(5)).resolvedAt(null).build());

		verify(emailJsClient, never()).send(any(), any(), any());
	}

	@Test
	void deliveryFailureDoesNotPropagate() {
		recipient("alice@example.com", true);
		when(emailJsClient.send(any(), any(), any())).thenReturn(false);

		assertThatCode(() -> notificationService.handleIncidentEvent(IncidentEvent.builder()
				.monitorId(monitor.getId()).monitorName(monitor.getName()).monitorUrl(monitor.getUrl())
				.incidentId(42).status(IncidentStatus.OPEN)
				.startedAt(LocalDateTime.now().minusMinutes(5)).resolvedAt(null).build()))
				.doesNotThrowAnyException();

		verify(emailJsClient, times(1)).send(any(), any(), any());
	}
}
