package com.project.api_monitor.model;

import java.time.LocalDateTime;

public record NotificationRecipientResponse(Integer id, String email, boolean enabled, LocalDateTime createdAt) {

	public static NotificationRecipientResponse from(NotificationRecipient recipient) {
		return new NotificationRecipientResponse(recipient.getId(), recipient.getEmail(),
				recipient.isEnabled(), recipient.getCreatedAt());
	}
}
