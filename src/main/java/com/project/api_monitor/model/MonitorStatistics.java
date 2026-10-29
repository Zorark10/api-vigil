package com.project.api_monitor.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MonitorStatistics {

	private Integer monitorId;
	private int totalChecks;
	private int successfulChecks;
	private int failedChecks;
	private double uptimePercentage;
	private double averageResponseTime;
	private Long minResponseTime;
	private Long maxResponseTime;
}
