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
 * (admin / test-admin-password, see application-test.properties).
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
}
