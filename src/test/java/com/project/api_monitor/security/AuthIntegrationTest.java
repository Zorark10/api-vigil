package com.project.api_monitor.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.project.api_monitor.model.AppUser;
import com.project.api_monitor.repository.AppUserRepo;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

	private static final Pattern TOKEN_PATTERN = Pattern.compile("\"token\":\"([^\"]+)\"");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private AppUserRepo appUserRepo;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AdminProvisioner adminProvisioner;

	private String login() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/login")
				.header("Content-Type", "application/json")
				.content("{\"username\":\"admin\",\"password\":\"test-admin-password\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andReturn();
		String body = result.getResponse().getContentAsString();
		Matcher matcher = TOKEN_PATTERN.matcher(body);
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}

	@Test
	void loginReturnsUsableToken() throws Exception {
		String token = login();

		mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("admin"))
				.andExpect(jsonPath("$.role").value("ADMIN"));
	}

	@Test
	void wrongPasswordReturns401() throws Exception {
		mockMvc.perform(post("/api/auth/login")
				.header("Content-Type", "application/json")
				.content("{\"username\":\"admin\",\"password\":\"wrong-password\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid username or password"));
	}

	@Test
	void unknownUserReturns401() throws Exception {
		mockMvc.perform(post("/api/auth/login")
				.header("Content-Type", "application/json")
				.content("{\"username\":\"ghost\",\"password\":\"whatever\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid username or password"));
	}

	@Test
	void passwordIsStoredHashedNotPlaintext() {
		AppUser user = appUserRepo.findByUsername("admin").orElseThrow();
		assertThat(user.getPasswordHash()).isNotEqualTo("test-admin-password");
		assertThat(user.getPasswordHash()).startsWith("$2");
		assertThat(passwordEncoder.matches("test-admin-password", user.getPasswordHash())).isTrue();
	}

	@Test
	void protectedEndpointRequiresToken() throws Exception {
		mockMvc.perform(get("/api/monitors"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Authentication required"));
	}

	@Test
	void validTokenGrantsAccessToProtectedEndpoints() throws Exception {
		String token = login();

		mockMvc.perform(get("/api/monitors").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
	}

	@Test
	void invalidTokenReturns401() throws Exception {
		mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-real-token"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid or expired token"));
	}

	@Test
	void expiredTokenReturns401() throws Exception {
		String token = jwtService.issueToken("admin", Instant.now().minus(Duration.ofHours(2)));

		mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid or expired token"));
	}

	@Test
	void provisioningDoesNotRecreateAdmin() {
		AppUser before = appUserRepo.findByUsername("admin").orElseThrow();
		long countBefore = appUserRepo.count();

		adminProvisioner.run(null);

		assertThat(appUserRepo.count()).isEqualTo(countBefore);
		AppUser after = appUserRepo.findByUsername("admin").orElseThrow();
		assertThat(after.getId()).isEqualTo(before.getId());
		assertThat(after.getPasswordHash()).isEqualTo(before.getPasswordHash());
	}
}
