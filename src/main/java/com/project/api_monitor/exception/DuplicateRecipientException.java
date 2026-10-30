package com.project.api_monitor.exception;

public class DuplicateRecipientException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public DuplicateRecipientException() {
		super("This email address is already a notification recipient");
	}
}
