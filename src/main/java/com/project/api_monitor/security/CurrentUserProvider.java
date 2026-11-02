package com.project.api_monitor.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.project.api_monitor.exception.CurrentUserNotFoundException;
import com.project.api_monitor.model.AppUser;
import com.project.api_monitor.repository.AppUserRepo;

import lombok.RequiredArgsConstructor;

/**
 * Resolves the account behind the authenticated JWT principal. The JWT
 * subject is the username; ownership of any resource is always derived from
 * this lookup, never from values supplied by the request.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserProvider {

	private final AppUserRepo userRepo;

	public AppUser currentUser(Jwt jwt) {
		if (jwt == null) {
			throw new CurrentUserNotFoundException();
		}
		return userRepo.findByUsername(jwt.getSubject())
				.orElseThrow(CurrentUserNotFoundException::new);
	}
}
