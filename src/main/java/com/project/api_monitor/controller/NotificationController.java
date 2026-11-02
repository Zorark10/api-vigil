package com.project.api_monitor.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.project.api_monitor.model.AddRecipientRequest;
import com.project.api_monitor.model.AppUser;
import com.project.api_monitor.model.NotificationRecipient;
import com.project.api_monitor.model.NotificationRecipientResponse;
import com.project.api_monitor.model.NotificationSettings;
import com.project.api_monitor.model.NotificationSettingsResponse;
import com.project.api_monitor.model.RecipientUpdateRequest;
import com.project.api_monitor.model.SettingsUpdateRequest;
import com.project.api_monitor.model.TestEmailResult;
import com.project.api_monitor.security.CurrentUserProvider;
import com.project.api_monitor.service.NotificationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationService notificationService;
	private final CurrentUserProvider currentUser;

	@GetMapping("/api/notifications/settings")
	public ResponseEntity<NotificationSettingsResponse> getSettings(@AuthenticationPrincipal Jwt jwt) {
		NotificationSettings settings = notificationService.getSettings(owner(jwt));
		return ResponseEntity.ok(NotificationSettingsResponse.from(settings));
	}

	@PutMapping("/api/notifications/settings")
	public ResponseEntity<NotificationSettingsResponse> updateSettings(
			@RequestBody @Valid SettingsUpdateRequest request, @AuthenticationPrincipal Jwt jwt) {
		notificationService.setEmailEnabled(owner(jwt), request.emailEnabled());
		return getSettings(jwt);
	}

	@GetMapping("/api/notifications/recipients")
	public ResponseEntity<List<NotificationRecipientResponse>> listRecipients(@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok(notificationService.listRecipients(owner(jwt)).stream()
				.map(NotificationRecipientResponse::from)
				.toList());
	}

	@PostMapping("/api/notifications/recipients")
	public ResponseEntity<NotificationRecipientResponse> addRecipient(
			@RequestBody @Valid AddRecipientRequest request, @AuthenticationPrincipal Jwt jwt) {
		NotificationRecipient recipient = notificationService.addRecipient(request.email(), owner(jwt));
		return ResponseEntity
				.created(java.net.URI.create("/api/notifications/recipients/" + recipient.getId()))
				.body(NotificationRecipientResponse.from(recipient));
	}

	@PatchMapping("/api/notifications/recipients/{id}")
	public ResponseEntity<NotificationRecipientResponse> updateRecipientEnabled(
			@PathVariable Integer id, @RequestBody @Valid RecipientUpdateRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		NotificationRecipient recipient = notificationService.setRecipientEnabled(id, request.enabled(), owner(jwt));
		return ResponseEntity.ok(NotificationRecipientResponse.from(recipient));
	}

	@DeleteMapping("/api/notifications/recipients/{id}")
	public ResponseEntity<Void> deleteRecipient(@PathVariable Integer id, @AuthenticationPrincipal Jwt jwt) {
		notificationService.removeRecipient(id, owner(jwt));
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/api/notifications/test")
	public ResponseEntity<TestEmailResult> sendTestEmail(@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok(notificationService.sendTestEmail(owner(jwt)));
	}

	private AppUser owner(Jwt jwt) {
		return currentUser.currentUser(jwt);
	}
}
