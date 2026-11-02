package com.project.api_monitor.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.project.api_monitor.model.AppUser;
import com.project.api_monitor.repository.AppUserRepo;
import com.project.api_monitor.support.TestAuth;

/**
 * POST /api/auth/register: validation, uniqueness, auto-authentication and
 * role-escalation hardening. The shared test database already contains the
 * provisioned administrator, so every registration here creates a USER.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistrationIntegrationTest {

	private static final Pattern TOKEN_PATTERN = Pattern.compile("\"token\":\"([^\"]+)\"");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AppUserRepo appUserRepo;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private String register(String username, String email, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/register")
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.content("{\"username\":\"" + username + "\",\"email\":\"" + email
						+ "\",\"password\":\"" + password + "\"}"))
				.andReturn().getResponse().getContentAsString();
	}

	private String extractToken(String body) {
		Matcher matcher = TOKEN_PATTERN.matcher(body);
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}

	@Test
	void registerCreatesUserAccountAndAutoAuthenticates() throws Exception {
		String body = register("reg-alice", "reg-alice@example.com", "password-123");
		assertThat(body).contains("\"token\"").contains("\"tokenType\":\"Bearer\"");

		String token = extractToken(body);

		mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("reg-alice"))
				.andExpect(jsonPath("$.role").value("USER"));
	}

	@Test
	void registeredUserPasswordStoredAsBcryptHash() throws Exception {
		String body = register("reg-hash", "reg-hash@example.com", "password-456");
		String token = extractToken(body);
		assertThat(token).isNotBlank();

		AppUser user = appUserRepo.findByUsername("reg-hash").orElseThrow();
		assertThat(user.getPasswordHash()).isNotEqualTo("password-456");
		assertThat(user.getPasswordHash()).startsWith("$2");
		assertThat(passwordEncoder.matches("password-456", user.getPasswordHash())).isTrue();
	}

	@Test
	void duplicateUsernameReturns409() throws Exception {
		register("reg-bob", "reg-bob@example.com", "password-123");

		mockMvc.perform(post("/api/auth/register")
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.content("{\"username\":\"reg-bob\",\"email\":\"other@example.com\",\"password\":\"password-123\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("That username is already taken"));
	}

	@Test
	void duplicateEmailReturns409() throws Exception {
		register("reg-carol", "reg-carol@example.com", "password-123");

		mockMvc.perform(post("/api/auth/register")
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.content("{\"username\":\"reg-dave\",\"email\":\"reg-carol@example.com\",\"password\":\"password-123\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("That email address is already registered"));
	}

	@Test
	void shortPasswordReturns400() throws Exception {
		mockMvc.perform(post("/api/auth/register")
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.content("{\"username\":\"reg-short\",\"email\":\"reg-short@example.com\",\"password\":\"short\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void invalidEmailReturns400() throws Exception {
		mockMvc.perform(post("/api/auth/register")
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.content("{\"username\":\"reg-bademail\",\"email\":\"not-an-email\",\"password\":\"password-123\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void invalidUsernameReturns400() throws Exception {
		mockMvc.perform(post("/api/auth/register")
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.content("{\"username\":\"no\",\"email\":\"reg-no@example.com\",\"password\":\"password-123\"}"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/auth/register")
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.content("{\"username\":\"bad name!\",\"email\":\"reg-bad@example.com\",\"password\":\"password-123\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void roleEscalationAttemptIsIgnored() throws Exception {
		String body = mockMvc.perform(post("/api/auth/register")
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.content("{\"username\":\"reg-hacker\",\"email\":\"reg-hacker@example.com\","
						+ "\"password\":\"password-123\",\"role\":\"ADMIN\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		String token = extractToken(body);
		mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("USER"));
	}

	@Test
	void setupStatusIsPublicAndReportsNoAdministratorNeeded() throws Exception {
		mockMvc.perform(get("/api/auth/setup-status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.administratorRequired").value(false));
	}

	@Test
	void setupStatusProtectedUsersCannotAlterIt() throws Exception {
		// Only GET is public; writing is never part of the bootstrap surface.
		mockMvc.perform(post("/api/auth/setup-status"))
				.andExpect(status().isUnauthorized());
	}
}
