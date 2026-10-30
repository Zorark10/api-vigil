package com.project.api_monitor.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.project.api_monitor.repository.NotificationRecipientRepo;
import com.project.api_monitor.repository.NotificationSettingsRepo;
import com.project.api_monitor.service.EmailDeliveryService;
import com.project.api_monitor.support.TestAuth;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private NotificationRecipientRepo recipientRepo;

	@Autowired
	private NotificationSettingsRepo settingsRepo;

	@MockitoBean
	private EmailDeliveryService emailDeliveryService;

	private String token;

	private String bearer() {
		return "Bearer " + token;
	}

	@BeforeEach
	void setUp() {
		recipientRepo.deleteAll();
		settingsRepo.deleteAll();
		token = TestAuth.login(mockMvc);
	}

	@Test
	void recipientsRequireAuthentication() throws Exception {
		mockMvc.perform(get("/api/notifications/recipients"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void listRecipientsInitiallyEmpty() throws Exception {
		mockMvc.perform(get("/api/notifications/recipients").header(HttpHeaders.AUTHORIZATION, bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void addRecipientNormalizesAndEnables() throws Exception {
		mockMvc.perform(post("/api/notifications/recipients")
				.header(HttpHeaders.AUTHORIZATION, bearer())
				.header("Content-Type", "application/json")
				.content("{\"email\":\"Ops@Example.COM\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value("ops@example.com"))
				.andExpect(jsonPath("$.enabled").value(true));

		assertThat(recipientRepo.findAllByOrderByIdAsc()).hasSize(1);
	}

	@Test
	void addDuplicateRecipientReturns409() throws Exception {
		notificationAdd("ops@example.com");
		mockMvc.perform(post("/api/notifications/recipients")
				.header(HttpHeaders.AUTHORIZATION, bearer())
				.header("Content-Type", "application/json")
				.content("{\"email\":\"ops@example.com\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("This email address is already a notification recipient"));
	}

	@Test
	void addInvalidRecipientReturns400() throws Exception {
		addAndExpectStatus("not-an-email", 400);
	}

	@Test
	void addBlankRecipientReturns400() throws Exception {
		mockMvc.perform(post("/api/notifications/recipients")
				.header(HttpHeaders.AUTHORIZATION, bearer())
				.header("Content-Type", "application/json")
				.content("{\"email\":\"\"}"))
				.andExpect(status().isBadRequest());
	}

	private void notificationAdd(String email) {
		try {
			mockMvc.perform(post("/api/notifications/recipients")
					.header(HttpHeaders.AUTHORIZATION, bearer())
					.header("Content-Type", "application/json")
					.content("{\"email\":\"" + email + "\"}"))
					.andExpect(status().isCreated());
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	private void addAndExpectStatus(String email, int status) throws Exception {
		mockMvc.perform(post("/api/notifications/recipients")
				.header(HttpHeaders.AUTHORIZATION, bearer())
				.header("Content-Type", "application/json")
				.content("{\"email\":\"" + email + "\"}"))
				.andExpect(status().is(status));
	}

	@Test
	void toggleRecipientDisabled() throws Exception {
		notificationAdd("a@x.io");

		Long id = recipientRepo.findAllByOrderByIdAsc().get(0).getId().longValue();

		mockMvc.perform(patch("/api/notifications/recipients/" + id)
				.header(HttpHeaders.AUTHORIZATION, bearer())
				.header("Content-Type", "application/json")
				.content("{\"enabled\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.enabled").value(false));

		assertThat(recipientRepo.findById(id.intValue()).orElseThrow().isEnabled()).isFalse();
	}

	@Test
	void toggleUnknownRecipientReturns404() throws Exception {
		mockMvc.perform(patch("/api/notifications/recipients/999")
				.header(HttpHeaders.AUTHORIZATION, bearer())
				.header("Content-Type", "application/json")
				.content("{\"enabled\":false}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Recipient not found with id: 999"));
	}

	@Test
	void deleteRecipient() throws Exception {
		notificationAdd("a@x.io");
		Long id = recipientRepo.findAllByOrderByIdAsc().get(0).getId().longValue();

		mockMvc.perform(delete("/api/notifications/recipients/" + id)
				.header(HttpHeaders.AUTHORIZATION, bearer()))
				.andExpect(status().isNoContent());

		assertThat(recipientRepo.count()).isZero();

		mockMvc.perform(delete("/api/notifications/recipients/" + id)
				.header(HttpHeaders.AUTHORIZATION, bearer()))
				.andExpect(status().isNotFound());
	}

	@Test
	void settingsDefaultToEnabled() throws Exception {
		mockMvc.perform(get("/api/notifications/settings").header(HttpHeaders.AUTHORIZATION, bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.emailEnabled").value(true));
	}

	@Test
	void updateSettings() throws Exception {
		mockMvc.perform(put("/api/notifications/settings")
				.header(HttpHeaders.AUTHORIZATION, bearer())
				.header("Content-Type", "application/json")
				.content("{\"emailEnabled\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.emailEnabled").value(false));
	}

	@Test
	void updateSettingsWithoutValueReturns400() throws Exception {
		mockMvc.perform(put("/api/notifications/settings")
				.header(HttpHeaders.AUTHORIZATION, bearer())
				.header("Content-Type", "application/json")
				.content("{}"))
				.andExpect(status().isBadRequest());
	}
}
