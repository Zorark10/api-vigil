package com.project.api_monitor.exception;

/** Registration tried to occupy a username or email that already exists. */
public class DuplicateUserException extends RuntimeException {

	public DuplicateUserException(String message) {
		super(message);
	}
}
