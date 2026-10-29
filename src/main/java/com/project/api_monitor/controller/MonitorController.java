package com.project.api_monitor.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.project.api_monitor.model.Monitor;
import com.project.api_monitor.model.MonitorResult;
import com.project.api_monitor.model.MonitorResultResponse;
import com.project.api_monitor.model.MonitorStatistics;
import com.project.api_monitor.model.MonitorStatusResponse;
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

	@PostMapping("/api/monitors")
	public Monitor addMonitor(@RequestBody @Valid Monitor monitor) {
		return service.addMonitor(monitor);
	}
	
	@GetMapping("/api/monitors")
	public List<Monitor> getAllMonitors(){
		return service.getAllMonitors();
	}
	
	@GetMapping("/api/monitors/{id}")
	public Monitor getMonitorById(@PathVariable Integer id) {
		return service.getMonitorById(id);
	}

	@PatchMapping("/api/monitors/{id}")
	public Monitor updateMonitor(@PathVariable Integer id, @RequestBody @Valid Monitor monitor) {
		return service.updateMonitor(id, monitor);
	}

	@DeleteMapping("/api/monitors/{id}")
	public void deleteMonitor(@PathVariable Integer id) {
		service.deleteMonitor(id);
	}
	
	@GetMapping("/api/monitors/redis-test/{id}")
	public Long redisTest(@PathVariable Integer id) {
		long timestamp = System.currentTimeMillis();
		redisSchedulerService.setNextCheck(id, timestamp);
		return redisSchedulerService.getNextCheck(id);
	}
	
	@GetMapping("/api/monitors/{id}/results")
	
	public Page<MonitorResultResponse> getResults(@PathVariable Integer id, @PageableDefault(size = 20) Pageable pageable) {
		Monitor monitor = service.getMonitorById(id);
		return monitorResultService.getResultsByMonitorPaginated(monitor, pageable);
	}
	
	@GetMapping("/api/monitors/{id}/statistics")
	public MonitorStatistics getStatistics(@PathVariable Integer id) {
		Monitor monitor = service.getMonitorById(id);
		return monitorResultService.getStatistics(monitor);
	}
	
	@GetMapping("/api/monitors/{id}/status")
	public Optional<MonitorStatusResponse> getStatus(@PathVariable Integer id) {
		Monitor monitor = service.getMonitorById(id);
		Optional<MonitorStatusResponse> response = monitorResultService.getCurrentStatus(monitor);
		return response;		
		
	}
}