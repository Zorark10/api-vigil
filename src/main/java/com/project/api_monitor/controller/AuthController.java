package com.project.api_monitor.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.project.api_monitor.model.AppUser;
import com.project.api_monitor.model.RegisterRequest;
import com.project.api_monitor.model.SetupStatusResponse;
import com.project.api_monitor.repository.AppUserRepo;
import com.project.api_monitor.security.JwtService;
import com.project.api_monitor.service.RegistrationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AuthController {

	private final AuthenticationManager authenticationManager;
	private final JwtService jwtService;
	private final AppUserRepo appUserRepo;
	private final RegistrationService registrationService;

	public record LoginRequest(@NotBlank String username, @NotBlank String password) {
	}

	@PostMapping("/api/auth/login")
	public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
		Authentication authentication = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(request.username(), request.password()));
		String token = jwtService.issueToken(authentication.getName());
		return ResponseEntity.ok(new LoginResponse(token, "Bearer"));
	}

	/**
	 * Account creation. Validates and hashes via RegistrationService, assigns
	 * the role server-side (USER, or ADMIN for the very first account when the
	 * instance has no users) and immediately authenticates the new user, so
	 * the frontend can store the returned token without a second request.
	 */
	@PostMapping("/api/auth/register")
	public ResponseEntity<LoginResponse> register(@RequestBody @Valid RegisterRequest request) {
		AppUser created = registrationService.register(request);
		Authentication authentication = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(created.getUsername(), request.password()));
		String token = jwtService.issueToken(authentication.getName());
		return ResponseEntity.status(HttpStatus.CREATED).body(new LoginResponse(token, "Bearer"));
	}

	/**
	 * Public bootstrap status (no users yet -> the next registration becomes
	 * the initial administrator).
	 */
	@GetMapping("/api/auth/setup-status")
	public ResponseEntity<SetupStatusResponse> setupStatus() {
		return ResponseEntity.ok(new SetupStatusResponse(registrationService.administratorRequired()));
	}

	@GetMapping("/api/auth/me")
	public ResponseEntity<MeResponse> me(@AuthenticationPrincipal Jwt jwt) {
		AppUser user = appUserRepo.findByUsername(jwt.getSubject()).orElseThrow();
		return ResponseEntity.ok(MeResponse.from(user));
	}
}
