package com.project.api_monitor.model;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentEvent {
	private Integer monitorId;
	private String monitorName;
	private String monitorUrl;
	private Integer incidentId;
	private IncidentStatus status;
	private LocalDateTime startedAt;
	private LocalDateTime resolvedAt;
	
}
