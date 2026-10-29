package com.project.api_monitor.controller;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.project.api_monitor.model.IncidentResponse;
import com.project.api_monitor.model.IncidentStatus;
import com.project.api_monitor.model.Monitor;
import com.project.api_monitor.service.IncidentService;
import com.project.api_monitor.service.MonitorService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class IncidentController {

	private final MonitorService monitorService;
	private final IncidentService incidentService;

	@GetMapping("/api/monitors/{id}/incidents")
	public Page<IncidentResponse> getIncidentsForMonitor(@PathVariable Integer id,
			@PageableDefault(size = 20) Pageable pageable) {
		Monitor monitor = monitorService.getMonitorById(id);
		return incidentService.getIncidentsForMonitor(monitor, pageable);
	}

	@GetMapping("/api/incidents")
	public Page<IncidentResponse> getIncidents(
			@RequestParam Optional<IncidentStatus> status,
			@PageableDefault(size = 20) Pageable pageable) {
		return incidentService.getIncidents(status, pageable);
	}
}
