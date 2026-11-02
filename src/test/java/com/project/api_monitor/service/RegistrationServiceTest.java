package com.project.api_monitor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.project.api_monitor.model.AppUser;
import com.project.api_monitor.model.NotificationSettings;
import com.project.api_monitor.model.RegisterRequest;
import com.project.api_monitor.repository.AppUserRepo;
import com.project.api_monitor.repository.NotificationSettingsRepo;

/**
 * Role/bootstrap decision of the registration flow. The "first account
 * becomes administrator" rule cannot be exercised in the shared test context
 * (the provisioned admin already exists there), so the decision itself is
 * tested directly; the empty-database integration path is covered by the
 * setup-status wiring and the ADMIN branch decision below.
 */
@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

	@Mock
	private AppUserRepo userRepo;

	@Mock
	private NotificationSettingsRepo settingsRepo;

	@Mock
	private PasswordEncoder passwordEncoder;

	@InjectMocks
	private RegistrationService service;

	@Mock
	private PlatformTransactionManager txManager;

	@BeforeEach
	void stubTransactionTemplate() {
		lenient().when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
	}

	private RegisterRequest request(String username) {
		return new RegisterRequest(username, username + "@example.com", "long-enough-password");
	}

	@Test
	void firstAccountOnEmptyInstanceBecomesAdministrator() {
		when(userRepo.existsByUsername("solo")).thenReturn(false);
		when(userRepo.existsByEmail("solo@example.com")).thenReturn(false);
		when(userRepo.count()).thenReturn(0L);
		when(passwordEncoder.encode("long-enough-password")).thenReturn("bcrypt-hash");
		when(userRepo.saveAndFlush(any())).thenAnswer(inv -> {
			AppUser saved = inv.getArgument(0);
			saved.setId(1);
			saved.setCreatedAt(LocalDateTime.now());
			return saved;
		});

		AppUser created = service.register(request("solo"));

		assertThat(created.getRole()).isEqualTo("ADMIN");
		assertThat(created.getPasswordHash()).isEqualTo("bcrypt-hash");
		ArgumentCaptor<NotificationSettings> settings = ArgumentCaptor.forClass(NotificationSettings.class);
		verify(settingsRepo).save(settings.capture());
		assertThat(settings.getValue().getOwner()).isSameAs(created);
		assertThat(settings.getValue().isEmailEnabled()).isTrue();
	}

	@Test
	void laterAccountsArePlainUsers() {
		when(userRepo.existsByUsername("second")).thenReturn(false);
		when(userRepo.existsByEmail("second@example.com")).thenReturn(false);
		when(userRepo.count()).thenReturn(1L);
		when(passwordEncoder.encode("long-enough-password")).thenReturn("bcrypt-hash");
		when(userRepo.saveAndFlush(any())).thenAnswer(inv -> {
			AppUser saved = inv.getArgument(0);
			saved.setId(2);
			saved.setCreatedAt(LocalDateTime.now());
			return saved;
		});

		assertThat(service.register(request("second")).getRole()).isEqualTo("USER");
	}

	@Test
	void duplicateUsernameIsRejected() {
		when(userRepo.existsByUsername("taken")).thenReturn(true);

		try {
			service.register(request("taken"));
			throw new AssertionError("expected DuplicateUserException");
		} catch (com.project.api_monitor.exception.DuplicateUserException ex) {
			verify(userRepo, never()).save(any());
		}
	}

	@Test
	void duplicateEmailIsRejected() {
		when(userRepo.existsByUsername("alice")).thenReturn(false);
		when(userRepo.existsByEmail("alice@example.com")).thenReturn(true);

		try {
			service.register(request("alice"));
			throw new AssertionError("expected DuplicateUserException");
		} catch (com.project.api_monitor.exception.DuplicateUserException ex) {
			verify(userRepo, never()).save(any());
		}
	}
}
