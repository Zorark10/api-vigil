package com.project.api_monitor.service;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.project.api_monitor.exception.DuplicateUserException;
import com.project.api_monitor.model.AppUser;
import com.project.api_monitor.model.NotificationSettings;
import com.project.api_monitor.model.RegisterRequest;
import com.project.api_monitor.repository.AppUserRepo;
import com.project.api_monitor.repository.NotificationSettingsRepo;

import lombok.extern.slf4j.Slf4j;

/**
 * Account creation. Passwords are stored as BCrypt hashes only; roles are
 * decided entirely server-side — USER for every registration, except that the
 * very first account created on an empty instance becomes the initial
 * administrator ("real" administrator setup when no users exist). Each new
 * account gets its own mailbox-allowing notification settings row so every
 * workspace starts with email alerts enabled.
 *
 * The empty-instance decision is atomic: the registration body runs inside a
 * single JVM-wide monitor that HOLDS its commit before releasing (the
 * transaction is committed by TransactionTemplate.execute inside the
 * synchronized block, not by a proxy after it). Two simultaneous
 * registrations therefore can never both observe an empty user table — the
 * second one always sees the first account and receives USER.
 */
@Service
@Slf4j
public class RegistrationService {

	/** All registration work is serialized on the instance monitor. */
	private final AppUserRepo userRepo;
	private final NotificationSettingsRepo settingsRepo;
	private final PasswordEncoder passwordEncoder;
	private final TransactionTemplate txTemplate;
	private final Object registrationLock = new Object();

	public RegistrationService(AppUserRepo userRepo, NotificationSettingsRepo settingsRepo,
			PasswordEncoder passwordEncoder, PlatformTransactionManager transactionManager) {
		this.userRepo = userRepo;
		this.settingsRepo = settingsRepo;
		this.passwordEncoder = passwordEncoder;
		this.txTemplate = new TransactionTemplate(transactionManager);
	}

	public boolean administratorRequired() {
		return userRepo.count() == 0;
	}

	public AppUser register(RegisterRequest request) {
		String username = request.username().trim();
		String email = request.email().trim().toLowerCase(Locale.ROOT);

		synchronized (registrationLock) {
			return txTemplate.execute(status -> {
				try {
					if (userRepo.existsByUsername(username)) {
						throw new DuplicateUserException("That username is already taken");
					}
					if (userRepo.existsByEmail(email)) {
						throw new DuplicateUserException("That email address is already registered");
					}

					String role = administratorRequired() ? "ADMIN" : "USER";
					AppUser user = userRepo.saveAndFlush(AppUser.builder()
							.username(username)
							.email(email)
							.passwordHash(passwordEncoder.encode(request.password()))
							.role(role)
							.createdAt(java.time.LocalDateTime.now())
							.build());
					settingsRepo.save(NotificationSettings.builder()
							.owner(user)
							.emailEnabled(true)
							.build());
					log.info("Created {} account '{}' (first account: {})", role, username, role.equals("ADMIN"));
					return user;
				} catch (DataIntegrityViolationException ex) {
					// A concurrent registration slipped past the exists checks.
					throw new DuplicateUserException("That username or email address is already registered");
				}
			});
		}
	}
}
