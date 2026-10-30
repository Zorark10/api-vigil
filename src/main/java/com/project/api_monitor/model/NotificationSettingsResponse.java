package com.project.api_monitor.model;

public record NotificationSettingsResponse(boolean emailEnabled) {

	public static NotificationSettingsResponse from(NotificationSettings settings) {
		return new NotificationSettingsResponse(settings.isEmailEnabled());
	}
}
