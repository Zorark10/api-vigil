package com.project.api_monitor.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.project.api_monitor.model.AppUser;
import com.project.api_monitor.model.HttpMethod;
import com.project.api_monitor.model.Incident;
import com.project.api_monitor.model.IncidentStatus;
import com.project.api_monitor.model.Monitor;
import com.project.api_monitor.model.MonitorResult;
import com.project.api_monitor.model.MonitorStatus;
import com.project.api_monitor.repository.AppUserRepo;
import com.project.api_monitor.repository.IncidentRepository;
import com.project.api_monitor.repository.MonitorRepo;
import com.project.api_monitor.repository.MonitorResultRepo;
import com.project.api_monitor.service.IncidentEventProducer;
import com.project.api_monitor.service.RedisSchedulerService;
import com.project.api_monitor.support.TestAuth;

/**
 * DELETE /api/monitors/{id} must also remove the monitor's check results and
 * incidents (they reference the monitor via foreign keys), in one
 * transaction, without emitting incident-resolution events or emails, and
 * must keep the 404 for unknown ids.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MonitorDeletionTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MonitorRepo monitorRepo;

	@Autowired
	private MonitorResultRepo monitorResultRepo;

	@Autowired
	private IncidentRepository incidentRepo;

	@Autowired
	private AppUserRepo appUserRepo;

	@MockitoBean
	private IncidentEventProducer incidentEventProducer;

	@MockitoBean
	private RedisSchedulerService redisSchedulerService;

	@MockitoBean
	private com.project.api_monitor.service.EmailJsClient emailJsClient;

	private String token;

	private AppUser admin;

	private String bearer() {
		return "Bearer " + token;
	}

	@BeforeEach
	void setUp() {
		incidentRepo.deleteAll();
		monitorResultRepo.deleteAll();
		monitorRepo.deleteAll();
		token = TestAuth.login(mockMvc);
		admin = appUserRepo.findByUsername("admin").orElseThrow();
	}

	private Monitor monitor(String name) {
		return monitorRepo.save(Monitor.builder()
				.owner(admin)
				.name(name)
				.url("https://" + name.toLowerCase().replace(' ', '-') + ".example.com/health")
				.interval(60)
				.timeout(5000)
				.enabled(true)
				.createdAt(LocalDateTime.now())
				.httpMethod(HttpMethod.GET)
				.build());
	}

	private MonitorResult result(Monitor monitor, MonitorStatus status) {
		return monitorResultRepo.save(MonitorResult.builder()
				.monitor(monitor)
				.status(status)
				.statusCode(status == MonitorStatus.UP ? 200 : null)
				.responseTime(120L)
				.checkedAt(LocalDateTime.now())
				.build());
	}

	private Incident incident(Monitor monitor, IncidentStatus status, boolean resolved) {
		LocalDateTime startedAt = LocalDateTime.now().minusHours(1);
		return incidentRepo.save(Incident.builder()
				.monitor(monitor)
				.status(status)
				.startedAt(startedAt)
				.resolvedAt(resolved ? startedAt.plusMinutes(15) : null)
				.build());
	}

	@Test
	void deletingMonitorWithHistoryRemovesResultsAndIncidentsAndMonitor() throws Exception {
		Monitor monitor = monitor("Billing API");
		Monitor other = monitor("Search API");
		result(monitor, MonitorStatus.UP);
		result(monitor, MonitorStatus.DOWN);
		result(other, MonitorStatus.UP);
		incident(monitor, IncidentStatus.OPEN, false);
		incident(monitor, IncidentStatus.RESOLVED, true);
		incident(other, IncidentStatus.RESOLVED, true);

		mockMvc.perform(delete("/api/monitors/" + monitor.getId()).header(HttpHeaders.AUTHORIZATION, bearer()))
				.andExpect(status().isNoContent());

		assertThat(monitorRepo.findById(monitor.getId())).isEmpty();
		assertThat(monitorResultRepo.findByMonitor(monitor)).isEmpty();
		assertThat(incidentRepo.findByMonitorOrderByStartedAtDesc(monitor, Pageable.unpaged())).isEmpty();

		// The other monitor's records are untouched.
		assertThat(monitorRepo.findById(other.getId())).isPresent();
		assertThat(monitorResultRepo.findByMonitor(other)).hasSize(1);
		assertThat(incidentRepo.findByMonitorOrderByStartedAtDesc(other, Pageable.unpaged())).hasSize(1);

		// All monitor endpoints answer 404 for the deleted monitor...
		mockMvc.perform(get("/api/monitors").header(HttpHeaders.AUTHORIZATION, bearer()))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/monitors/" + monitor.getId()).header(HttpHeaders.AUTHORIZATION, bearer()))
				.andExpect(status().isNotFound());

		// ...and no incident-resolution event or email was produced by the delete.
		verify(incidentEventProducer, never()).sendIncidentEvent(any());
		verify(emailJsClient, never()).send(any(), any(), any());
		verify(redisSchedulerService).deleteMonitorCache(monitor.getId());
	}

	@Test
	void deletingMonitorWithoutHistoryRemovesOnlyTheMonitor() throws Exception {
		Monitor monitor = monitor("Fresh API");

		mockMvc.perform(delete("/api/monitors/" + monitor.getId()).header(HttpHeaders.AUTHORIZATION, bearer()))
				.andExpect(status().isNoContent());

		assertThat(monitorRepo.count()).isZero();
		assertThat(monitorResultRepo.count()).isZero();
		assertThat(incidentRepo.count()).isZero();
		verify(redisSchedulerService).deleteMonitorCache(monitor.getId());
	}

	@Test
	void deletingUnknownMonitorReturns404WithoutSideEffects() throws Exception {
		mockMvc.perform(delete("/api/monitors/999").header(HttpHeaders.AUTHORIZATION, bearer()))
				.andExpect(status().isNotFound());

		assertThat(monitorRepo.count()).isZero();
		verify(redisSchedulerService, never()).deleteMonitorCache(999);
	}

	@Test
	void deletionRequiresAuthentication() throws Exception {
		Monitor monitor = monitor("Locked API");

		mockMvc.perform(delete("/api/monitors/" + monitor.getId()))
				.andExpect(status().isUnauthorized());

		assertThat(monitorRepo.findById(monitor.getId())).isPresent();
	}
}
