package com.project.api_monitor.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test helper that obtains a JWT from the provisioned test administrator
 * (admin / test-admin-password, see application-test.properties) and can
 * register brand-new accounts (USER role) for cross-account test scenarios.
 */
public final class TestAuth {

	private static final Pattern TOKEN_PATTERN = Pattern.compile("\"token\":\"([^\"]+)\"");

	private TestAuth() {
	}

	public static String login(MockMvc mockMvc) {
		try {
			MvcResult result = mockMvc.perform(post("/api/auth/login")
					.header("Content-Type", "application/json")
					.content("{\"username\":\"admin\",\"password\":\"test-admin-password\"}"))
					.andExpect(status().isOk())
					.andReturn();
			String body = result.getResponse().getContentAsString();
			Matcher matcher = TOKEN_PATTERN.matcher(body);
			assertThat(matcher.find()).isTrue();
			return matcher.group(1);
		} catch (Exception ex) {
			throw new IllegalStateException("Login as test admin failed", ex);
		}
	}

	/**
	 * Registers a fresh account and returns the auto-issued JWT. The account
	 * is a plain USER (an administrator already exists in this database).
	 */
	public static String registerAndLogin(MockMvc mockMvc, String username, String email, String password) {
		try {
			MvcResult result = mockMvc.perform(post("/api/auth/register")
					.header("Content-Type", "application/json")
					.content("{\"username\":\"" + username + "\",\"email\":\"" + email
							+ "\",\"password\":\"" + password + "\"}"))
					.andExpect(status().isCreated())
					.andReturn();
			String body = result.getResponse().getContentAsString();
			Matcher matcher = TOKEN_PATTERN.matcher(body);
			assertThat(matcher.find()).isTrue();
			return matcher.group(1);
		} catch (Exception ex) {
			throw new IllegalStateException("Registering test account " + username + " failed", ex);
		}
	}
}
