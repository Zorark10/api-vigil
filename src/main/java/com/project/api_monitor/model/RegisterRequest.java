package com.project.api_monitor.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registration payload. Deliberately contains no role field: accounts always
 * get the USER role server-side (the very first account on an empty instance
 * becomes the ADMIN), so a client can never register itself as an
 * administrator.
 */
public record RegisterRequest(
		@NotBlank
		@Size(min = 3, max = 32)
		@Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.-]*",
				message = "must start with a letter or digit and may only contain letters, digits, dots, dashes and underscores")
		String username,

		@NotBlank
		@Email
		String email,

		@NotBlank
		@Size(min = 8, max = 128)
		String password) {
}
