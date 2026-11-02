package com.project.api_monitor.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import com.project.api_monitor.service.EmailJsClient;
import com.project.api_monitor.service.IncidentEventProducer;
import com.project.api_monitor.service.RedisSchedulerService;
import com.project.api_monitor.support.TestAuth;

/**
 * The core isolation guarantee: two independently registered accounts plus
 * the provisioned administrator each get their own monitoring workspace.
 * Every cross-account read, modification and deletion must be rejected,
 * indistinguishable from a missing resource (404 — no existence leak), and
 * the admin's pre-existing monitors stay functional.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountIsolationTest {

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
	private EmailJsClient emailJsClient;

	private String token;
	private String alice;
	private String bob;

	private AppUser aliceUser;
	private AppUser bobUser;
	private AppUser adminUser;
	private Monitor aliceMonitor;

	private String suffix;

	private String bearer() {
		return "Bearer " + token;
	}

	@BeforeAll
	void setUpUsers() throws Exception {
		suffix = UUID.randomUUID().toString().substring(0, 8);

		monitorRepo.deleteAll();
		incidentRepo.deleteAll();
		monitorResultRepo.deleteAll();

		adminUser = appUserRepo.findByUsername("admin").orElseThrow();
		alice = TestAuth.registerAndLogin(mockMvc,
				"iso-alice-" + suffix, "iso-alice-" + suffix + "@example.com", "password-123");
		bob = TestAuth.registerAndLogin(mockMvc,
				"iso-bob-" + suffix, "iso-bob-" + suffix + "@example.com", "password-123");
		token = alice;
		aliceUser = appUserRepo.findByUsername("iso-alice-" + suffix).orElseThrow();
		bobUser = appUserRepo.findByUsername("iso-bob-" + suffix).orElseThrow();

		// Alice's workspace, created through the API as her JWT principal.
		mockMvc.perform(post("/api/monitors")
				.header(HttpHeaders.AUTHORIZATION, bearer())
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.content("{\"name\":\"Alice Watcher\",\"url\":\"https://alice.example.com/health\","
						+ "\"interval\":60,\"timeout\":5000,\"httpMethod\":\"GET\"}"))
				.andExpect(status().isOk());
		aliceMonitor = monitorRepo.findAll().stream()
				.filter(m -> "Alice Watcher".equals(m.getName()))
				.findFirst().orElseThrow();

		// History through the repositories (the Kafka pipeline stores the
		// same entities).
		monitorResultRepo.save(MonitorResult.builder()
				.monitor(aliceMonitor).status(MonitorStatus.DOWN).checkedAt(LocalDateTime.now()).build());
		incidentRepo.save(Incident.builder()
				.monitor(aliceMonitor).status(IncidentStatus.OPEN)
				.startedAt(LocalDateTime.now().minusMinutes(10)).build());

		// The administrator's own monitor (assigned by the migration / API).
		monitorRepo.save(Monitor.builder()
				.owner(adminUser).name("Admin Legacy Monitor")
				.url("https://admin.example.com/health").interval(60).timeout(5000)
				.enabled(true).createdAt(LocalDateTime.now()).httpMethod(com.project.api_monitor.model.HttpMethod.GET)
				.build());
	}

	private String aliceBearer() {
		return "Bearer " + alice;
	}

	private String bobBearer() {
		return "Bearer " + bob;
	}

	// ---- monitors ----

	@Test
	void bobCanNeverReadAlicesMonitor() throws Exception {
		mockMvc.perform(get("/api/monitors/" + aliceMonitor.getId()).header(HttpHeaders.AUTHORIZATION, bobBearer()))
				.andExpect(status().isNotFound());
	}

	@Test
	void monitorListsAreWorkspaceOnly() throws Exception {
		mockMvc.perform(get("/api/monitors").header(HttpHeaders.AUTHORIZATION, bobBearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.name == 'Alice Watcher')]").isEmpty());

		mockMvc.perform(get("/api/monitors").header(HttpHeaders.AUTHORIZATION, aliceBearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.name == 'Alice Watcher')]").isNotEmpty())
				.andExpect(jsonPath("$[?(@.name == 'Admin Legacy Monitor')]").isEmpty());

		// The provisioned administrator still sees its (migrated) monitor.
		String admin = TestAuth.login(mockMvc);
		mockMvc.perform(get("/api/monitors").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.name == 'Admin Legacy Monitor')]").isNotEmpty())
				.andExpect(jsonPath("$[?(@.name == 'Alice Watcher')]").isEmpty());
	}

	@Test
	void bobCannotModifyOrDeleteAlicesMonitor() throws Exception {
		mockMvc.perform(patch("/api/monitors/" + aliceMonitor.getId())
				.header(HttpHeaders.AUTHORIZATION, bobBearer())
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.content("{\"name\":\"Hijacked\",\"url\":\"https://alice.example.com/health\","
						+ "\"interval\":1,\"timeout\":1,\"httpMethod\":\"POST\"}"))
				.andExpect(status().isNotFound());

		Long monitorId = aliceMonitor.getId().longValue();
		mockMvc.perform(delete("/api/monitors/" + monitorId).header(HttpHeaders.AUTHORIZATION, bobBearer()))
				.andExpect(status().isNotFound());

		assertThat(monitorRepo.findById(aliceMonitor.getId())).isPresent();
		assertThat(monitorRepo.findById(aliceMonitor.getId()).orElseThrow().getName())
				.isEqualTo("Alice Watcher");
	}

	@Test
	void bobCannotReadAlicesCheckHistoryStatisticsStatusOrIncidents() throws Exception {
		Long monitorId = aliceMonitor.getId().longValue();
		mockMvc.perform(get("/api/monitors/" + monitorId + "/results")
				.header(HttpHeaders.AUTHORIZATION, bobBearer()))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/monitors/" + monitorId + "/statistics")
				.header(HttpHeaders.AUTHORIZATION, bobBearer()))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/monitors/" + monitorId + "/status")
				.header(HttpHeaders.AUTHORIZATION, bobBearer()))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/monitors/" + monitorId + "/incidents")
				.header(HttpHeaders.AUTHORIZATION, bobBearer()))
				.andExpect(status().isNotFound());

		// Alice herself sees everything just fine.
		mockMvc.perform(get("/api/monitors/" + monitorId + "/results")
				.header(HttpHeaders.AUTHORIZATION, aliceBearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1));
		mockMvc.perform(get("/api/monitors/" + monitorId + "/incidents")
				.header(HttpHeaders.AUTHORIZATION, aliceBearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void globalIncidentFeedIsWorkspaceScoped() throws Exception {
		mockMvc.perform(get("/api/incidents")
				.header(HttpHeaders.AUTHORIZATION, bobBearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));

		mockMvc.perform(get("/api/incidents")
				.header(HttpHeaders.AUTHORIZATION, aliceBearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1));

		mockMvc.perform(get("/api/incidents?status=OPEN")
				.header(HttpHeaders.AUTHORIZATION, bobBearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));

		// Admin sees only its own (legacy) workspace — none here.
		String admin = TestAuth.login(mockMvc);
		mockMvc.perform(get("/api/incidents").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	// ---- notifications ----

	@Test
	void recipientsAreScopedPerAccount() throws Exception {
		String email = "shared-" + suffix + "@example.com";

		mockMvc.perform(post("/api/notifications/recipients")
				.header(HttpHeaders.AUTHORIZATION, aliceBearer())
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.content("{\"email\":\"" + email + "\"}"))
				.andExpect(status().isCreated());

		// The same address may exist for Bob's independent workspace.
		mockMvc.perform(post("/api/notifications/recipients")
				.header(HttpHeaders.AUTHORIZATION, bobBearer())
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.content("{\"email\":\"" + email + "\"}"))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/notifications/recipients")
				.header(HttpHeaders.AUTHORIZATION, aliceBearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
		mockMvc.perform(get("/api/notifications/recipients")
				.header(HttpHeaders.AUTHORIZATION, bobBearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));

		// Duplicate within one workspace is still rejected.
		mockMvc.perform(post("/api/notifications/recipients")
				.header(HttpHeaders.AUTHORIZATION, aliceBearer())
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.content("{\"email\":\"" + email + "\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	void bobCannotModifyOrDeleteAlicesRecipients() throws Exception {
		java.util.Map.Entry<Long, String> guarded = createAliceRecipientForProbe();

		mockMvc.perform(patch("/api/notifications/recipients/" + guarded.getKey())
				.header(HttpHeaders.AUTHORIZATION, bobBearer())
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.content("{\"enabled\":false}"))
				.andExpect(status().isNotFound());

		mockMvc.perform(delete("/api/notifications/recipients/" + guarded.getKey())
				.header(HttpHeaders.AUTHORIZATION, bobBearer()))
				.andExpect(status().isNotFound());

		// Alice can still toggle her own recipient.
		mockMvc.perform(patch("/api/notifications/recipients/" + guarded.getKey())
				.header(HttpHeaders.AUTHORIZATION, aliceBearer())
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.content("{\"enabled\":false}"))
				.andExpect(status().isOk());
	}

	private java.util.Map.Entry<Long, String> createAliceRecipientForProbe() throws Exception {
		String email = "guarded-" + suffix + "@example.com";
		String body = mockMvc.perform(post("/api/notifications/recipients")
				.header(HttpHeaders.AUTHORIZATION, aliceBearer())
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.content("{\"email\":\"" + email + "\"}"))
				.andReturn().getResponse().getContentAsString();
		long id = Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
		return java.util.Map.entry(id, email);
	}

	@Test
	void settingsArePerAccount() throws Exception {
		mockMvc.perform(put("/api/notifications/settings")
				.header(HttpHeaders.AUTHORIZATION, aliceBearer())
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.content("{\"emailEnabled\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.emailEnabled").value(false));

		// Bob's switch is independent and stays enabled (its own row).
		mockMvc.perform(get("/api/notifications/settings")
				.header(HttpHeaders.AUTHORIZATION, bobBearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.emailEnabled").value(true));

		mockMvc.perform(get("/api/notifications/settings")
				.header(HttpHeaders.AUTHORIZATION, aliceBearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.emailEnabled").value(false));

		// Restore alice's switch for deterministic reruns.
		mockMvc.perform(put("/api/notifications/settings")
				.header(HttpHeaders.AUTHORIZATION, aliceBearer())
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.content("{\"emailEnabled\":true}"))
				.andExpect(status().isOk());
	}
}
