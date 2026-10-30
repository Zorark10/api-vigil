package com.project.api_monitor.controller;

import java.time.LocalDateTime;

import com.project.api_monitor.model.AppUser;

public record MeResponse(String username, String role, LocalDateTime createdAt) {

	public static MeResponse from(AppUser user) {
		return new MeResponse(user.getUsername(), user.getRole(), user.getCreatedAt());
	}
}
