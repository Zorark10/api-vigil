package com.project.api_monitor.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.project.api_monitor.exception.MonitorNotFoundException;
import com.project.api_monitor.model.IncidentResponse;
import com.project.api_monitor.model.IncidentStatus;
import com.project.api_monitor.service.IncidentService;
import com.project.api_monitor.service.MonitorService;

@WebMvcTest(IncidentController.class)
@AutoConfigureMockMvc(addFilters = false)
class IncidentControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MonitorService monitorService;

	@MockitoBean
	private IncidentService incidentService;

	private IncidentResponse sample(Integer id, Integer monitorId, IncidentStatus status) {
		IncidentResponse response = new IncidentResponse();
		response.setId(id);
		response.setMonitorId(monitorId);
		response.setStatus(status);
		response.setStartedAt(LocalDateTime.of(2025, 1, 1, 10, 0, 0));
		response.setResolvedAt(status == IncidentStatus.RESOLVED ? LocalDateTime.of(2025, 1, 1, 11, 0, 0) : null);
		return response;
	}

	@Test
	void incidentsByMonitorReturnsPaginatedIncidents() throws Exception {
		PageImpl<IncidentResponse> page = new PageImpl<>(List.of(sample(1, 5, IncidentStatus.OPEN),
				sample(2, 5, IncidentStatus.RESOLVED)));
		var monitor = new com.project.api_monitor.model.Monitor();
		when(monitorService.getMonitorById(5)).thenReturn(monitor);
		when(incidentService.getIncidentsForMonitor(eq(monitor), any())).thenReturn(page);

		mockMvc.perform(get("/api/monitors/5/incidents").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].monitorId").value(5))
				.andExpect(jsonPath("$.content[0].status").value("OPEN"))
				.andExpect(jsonPath("$.content[1].status").value("RESOLVED"));
	}

	@Test
	void incidentsByUnknownMonitorReturns404() throws Exception {
		when(monitorService.getMonitorById(999))
				.thenThrow(new MonitorNotFoundException(999));

		mockMvc.perform(get("/api/monitors/999/incidents").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Monitor not found with id: 999"));
	}

	@Test
	void incidentsListSupportsStatusFilter() throws Exception {
		PageImpl<IncidentResponse> page = new PageImpl<>(List.of(sample(1, 5, IncidentStatus.OPEN)));
		when(incidentService.getIncidents(eq(Optional.of(IncidentStatus.OPEN)), any()))
				.thenReturn(page);

		mockMvc.perform(get("/api/incidents").param("status", "OPEN").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].status").value("OPEN"))
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void incidentsListWithoutStatusReturnsAll() throws Exception {
		PageImpl<IncidentResponse> page = new PageImpl<>(List.of(sample(1, 5, IncidentStatus.OPEN),
				sample(2, 7, IncidentStatus.RESOLVED)));
		when(incidentService.getIncidents(eq(Optional.empty()), any())).thenReturn(page);

		mockMvc.perform(get("/api/incidents").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2));
	}
}
