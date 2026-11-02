package com.project.api_monitor.security;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.project.api_monitor.model.AppUser;
import com.project.api_monitor.repository.AppUserRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates the initial administrator from the ADMIN_USERNAME / ADMIN_PASSWORD
 * environment variables — but only while the app_user table is empty. Existing
 * users are never overwritten and the password is only ever persisted as a
 * BCrypt hash. On an empty database without ADMIN_PASSWORD the startup is not
 * blocked: bootstrapping the initial administrator is deferred to the first
 * registration (which becomes ADMIN when no users exist).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminProvisioner implements ApplicationRunner {

	private final AppUserRepo repo;
	private final PasswordEncoder passwordEncoder;

	@Value("${app.admin.username:admin}")
	private String adminUsername;

	@Value("${app.admin.password:}")
	private String adminPassword;

	@Override
	public void run(ApplicationArguments args) {
		if (repo.count() > 0) {
			return;
		}
		if (!StringUtils.hasText(adminPassword)) {
			log.info("No users yet and ADMIN_PASSWORD is not set; the initial administrator can be "
					+ "created by registering an account (the first one becomes ADMIN).");
			return;
		}
		repo.save(AppUser.builder()
				.username(adminUsername)
				.email(adminUsername.toLowerCase() + "@localhost.local")
				.passwordHash(passwordEncoder.encode(adminPassword))
				.role("ADMIN")
				.createdAt(LocalDateTime.now())
				.build());
		log.info("Provisioned initial administrator '{}'", adminUsername);
	}
}
