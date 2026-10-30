package com.project.api_monitor.exception;

public class RecipientNotFoundException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public RecipientNotFoundException(Integer id) {
		super("Recipient not found with id: " + id);
	}
}
