package com.project.api_monitor.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.project.api_monitor.model.AppUser;
import com.project.api_monitor.model.Monitor;
import com.project.api_monitor.model.MonitorResult;
import com.project.api_monitor.model.MonitorResultResponse;
import com.project.api_monitor.model.MonitorStatistics;
import com.project.api_monitor.model.MonitorStatusResponse;
import com.project.api_monitor.security.CurrentUserProvider;
import com.project.api_monitor.service.HealthCheckService;
import com.project.api_monitor.service.MonitorKafkaProducer;
import com.project.api_monitor.service.MonitorResultService;
import com.project.api_monitor.service.MonitorService;
import com.project.api_monitor.service.RedisSchedulerService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;


@RestController
@RequiredArgsConstructor
public class MonitorController {
	private final MonitorService service;

	private final MonitorResultService monitorResultService;

	private final RedisSchedulerService redisSchedulerService;

	private final CurrentUserProvider currentUser;

	@PostMapping("/api/monitors")
	public Monitor addMonitor(@RequestBody @Valid Monitor monitor, @AuthenticationPrincipal Jwt jwt) {
		return service.addMonitor(monitor, currentUser.currentUser(jwt));
	}

	@GetMapping("/api/monitors")
	public List<Monitor> getAllMonitors(@AuthenticationPrincipal Jwt jwt) {
		return service.getAllMonitors(currentUser.currentUser(jwt));
	}

	@GetMapping("/api/monitors/{id}")
	public Monitor getMonitorById(@PathVariable Integer id, @AuthenticationPrincipal Jwt jwt) {
		return service.getMonitorById(id, currentUser.currentUser(jwt));
	}

	@PatchMapping("/api/monitors/{id}")
	public Monitor updateMonitor(@PathVariable Integer id, @RequestBody @Valid Monitor monitor,
			@AuthenticationPrincipal Jwt jwt) {
		return service.updateMonitor(id, monitor, currentUser.currentUser(jwt));
	}

	@DeleteMapping("/api/monitors/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteMonitor(@PathVariable Integer id, @AuthenticationPrincipal Jwt jwt) {
		service.deleteMonitor(id, currentUser.currentUser(jwt));
	}

	@GetMapping("/api/monitors/redis-test/{id}")
	public Long redisTest(@PathVariable Integer id, @AuthenticationPrincipal Jwt jwt) {
		// Dev helper; it may only touch monitors of the own workspace.
		service.getMonitorById(id, currentUser.currentUser(jwt));
		long timestamp = System.currentTimeMillis();
		redisSchedulerService.setNextCheck(id, timestamp);
		return redisSchedulerService.getNextCheck(id);
	}

	@GetMapping("/api/monitors/{id}/results")

	public Page<MonitorResultResponse> getResults(@PathVariable Integer id,
			@PageableDefault(size = 20) Pageable pageable, @AuthenticationPrincipal Jwt jwt) {
		Monitor monitor = service.getMonitorById(id, currentUser.currentUser(jwt));
		return monitorResultService.getResultsByMonitorPaginated(monitor, pageable);
	}

	@GetMapping("/api/monitors/{id}/statistics")
	public MonitorStatistics getStatistics(@PathVariable Integer id, @AuthenticationPrincipal Jwt jwt) {
		Monitor monitor = service.getMonitorById(id, currentUser.currentUser(jwt));
		return monitorResultService.getStatistics(monitor);
	}

	@GetMapping("/api/monitors/{id}/status")
	public Optional<MonitorStatusResponse> getStatus(@PathVariable Integer id, @AuthenticationPrincipal Jwt jwt) {
		Monitor monitor = service.getMonitorById(id, currentUser.currentUser(jwt));
		Optional<MonitorStatusResponse> response = monitorResultService.getCurrentStatus(monitor);
		return response;

	}
}