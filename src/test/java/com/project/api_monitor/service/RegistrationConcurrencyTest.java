package com.project.api_monitor.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.project.api_monitor.model.AppUser;
import com.project.api_monitor.model.RegisterRequest;
import com.project.api_monitor.repository.AppUserRepo;
import com.project.api_monitor.repository.IncidentRepository;
import com.project.api_monitor.repository.MonitorRepo;
import com.project.api_monitor.repository.MonitorResultRepo;
import com.project.api_monitor.repository.NotificationRecipientRepo;
import com.project.api_monitor.repository.NotificationSettingsRepo;

/**
 * Proves the first-account role decision is atomic: many simultaneous
 * registrations on an emptied user table yield exactly one ADMIN, the rest
 * USER. The service itself commits inside the registration lock, so the plan
 * (commit release ordering) matches what is asserted here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RegistrationConcurrencyTest {

	@Autowired
	private RegistrationService registrationService;
	@Autowired
	private AppUserRepo userRepo;
	@Autowired
	private MonitorResultRepo resultRepo;
	@Autowired
	private IncidentRepository incidentRepo;
	@Autowired
	private MonitorRepo monitorRepo;
	@Autowired
	private NotificationRecipientRepo recipientRepo;
	@Autowired
	private NotificationSettingsRepo settingsRepo;

	@BeforeEach
	void wipeEverything() {
		resultRepo.deleteAll();
		incidentRepo.deleteAll();
		monitorRepo.deleteAll();
		recipientRepo.deleteAll();
		settingsRepo.deleteAll();
		userRepo.deleteAll();
	}

	@Test
	void simultaneousRegistrationsOnEmptyInstanceYieldExactlyOneAdministrator() throws Exception {
		int racers = 10;
		ExecutorService executor = Executors.newFixedThreadPool(racers);
		try {
			CountDownLatch start = new CountDownLatch(1);
			List<Callable<AppUser>> tasks = new ArrayList<>();
			for (int i = 0; i < racers; i++) {
				String username = "racer-" + i + "-" + UUID.randomUUID().toString().substring(0, 8);
				tasks.add(() -> {
					start.await();
					return registrationService.register(new RegisterRequest(
							username, username + "@example.com", "long-enough-password"));
				});
			}
			List<Future<AppUser>> futures = new ArrayList<>();
			for (Callable<AppUser> task : tasks) {
				futures.add(executor.submit(task));
			}
			start.countDown();

			List<AppUser> created = new ArrayList<>();
			for (Future<AppUser> f : futures) {
				created.add(f.get(30, TimeUnit.SECONDS));
			}

			// every racer completed, persisted and got its default settings row
			assertThat(created).extracting(AppUser::getUsername).doesNotHaveDuplicates();
			assertThat(created).allSatisfy(u -> {
				assertThat(u.getUsername()).startsWith("racer-");
				assertThat(settingsRepo.count()).isEqualTo(racers);
			});

			// exactly one ADMIN among the racing accounts, everyone else USER
			long admins = created.stream().filter(u -> "ADMIN".equals(u.getRole())).count();
			assertThat(admins).isEqualTo(1);
			assertThat(created).allSatisfy(u -> assertThat(u.getRole()).isIn("ADMIN", "USER"));

			// and the database agrees with what the callers were handed back
			assertThat(userRepo.count()).isEqualTo(racers);
			assertThat(userRepo.findAll().stream().filter(u -> "ADMIN".equals(u.getRole())).count()).isEqualTo(1);
		} finally {
			executor.shutdownNow();
		}
	}
}
