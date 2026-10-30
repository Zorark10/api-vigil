package com.project.api_monitor.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.project.api_monitor.model.AddRecipientRequest;
import com.project.api_monitor.model.NotificationRecipient;
import com.project.api_monitor.model.NotificationRecipientResponse;
import com.project.api_monitor.model.NotificationSettings;
import com.project.api_monitor.model.NotificationSettingsResponse;
import com.project.api_monitor.model.RecipientUpdateRequest;
import com.project.api_monitor.model.SettingsUpdateRequest;
import com.project.api_monitor.model.TestEmailResult;
import com.project.api_monitor.service.NotificationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationService notificationService;

	@GetMapping("/api/notifications/settings")
	public ResponseEntity<NotificationSettingsResponse> getSettings() {
		NotificationSettings settings = notificationService.getSettings();
		return ResponseEntity.ok(NotificationSettingsResponse.from(settings));
	}

	@PutMapping("/api/notifications/settings")
	public ResponseEntity<NotificationSettingsResponse> updateSettings(
			@RequestBody @Valid SettingsUpdateRequest request) {
		notificationService.setEmailEnabled(request.emailEnabled());
		return getSettings();
	}

	@GetMapping("/api/notifications/recipients")
	public ResponseEntity<List<NotificationRecipientResponse>> listRecipients() {
		return ResponseEntity.ok(notificationService.listRecipients().stream()
				.map(NotificationRecipientResponse::from)
				.toList());
	}

	@PostMapping("/api/notifications/recipients")
	public ResponseEntity<NotificationRecipientResponse> addRecipient(
			@RequestBody @Valid AddRecipientRequest request) {
		NotificationRecipient recipient = notificationService.addRecipient(request.email());
		return ResponseEntity
				.created(java.net.URI.create("/api/notifications/recipients/" + recipient.getId()))
				.body(NotificationRecipientResponse.from(recipient));
	}

	@PatchMapping("/api/notifications/recipients/{id}")
	public ResponseEntity<NotificationRecipientResponse> updateRecipientEnabled(
			@PathVariable Integer id, @RequestBody @Valid RecipientUpdateRequest request) {
		NotificationRecipient recipient = notificationService.setRecipientEnabled(id, request.enabled());
		return ResponseEntity.ok(NotificationRecipientResponse.from(recipient));
	}

	@DeleteMapping("/api/notifications/recipients/{id}")
	public ResponseEntity<Void> deleteRecipient(@PathVariable Integer id) {
		notificationService.removeRecipient(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/api/notifications/test")
	public ResponseEntity<TestEmailResult> sendTestEmail() {
		return ResponseEntity.ok(notificationService.sendTestEmail());
	}
}
