package com.project.api_monitor.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentResponse {
	private Integer id;
	private Integer monitorId;
	private IncidentStatus status;

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime startedAt;

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime resolvedAt;

	public static IncidentResponse from(Incident incident) {
		return IncidentResponse.builder()
				.id(incident.getId())
				.monitorId(incident.getMonitor().getId())
				.status(incident.getStatus())
				.startedAt(incident.getStartedAt())
				.resolvedAt(incident.getResolvedAt())
				.build();
	}
}
