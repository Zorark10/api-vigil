package com.project.api_monitor.exception;

/**
 * The JWT was accepted but the account behind it no longer exists (deleted or
 * wiped between issues). Treated as 401 so the client discards the token.
 */
public class CurrentUserNotFoundException extends RuntimeException {

	public CurrentUserNotFoundException() {
		super("Session is no longer valid");
	}
}
