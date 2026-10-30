package com.project.api_monitor.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.project.api_monitor.model.AppUser;
import com.project.api_monitor.repository.AppUserRepo;
import com.project.api_monitor.security.JwtService;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AuthController {

	private final AuthenticationManager authenticationManager;
	private final JwtService jwtService;
	private final AppUserRepo appUserRepo;

	public record LoginRequest(@NotBlank String username, @NotBlank String password) {
	}

	@PostMapping("/api/auth/login")
	public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
		Authentication authentication = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(request.username(), request.password()));
		String token = jwtService.issueToken(authentication.getName());
		return ResponseEntity.ok(new LoginResponse(token, "Bearer"));
	}

	@GetMapping("/api/auth/me")
	public ResponseEntity<MeResponse> me(@AuthenticationPrincipal Jwt jwt) {
		AppUser user = appUserRepo.findByUsername(jwt.getSubject()).orElseThrow();
		return ResponseEntity.ok(MeResponse.from(user));
	}
}
