package com.project.api_monitor.service;

import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import com.project.api_monitor.model.Monitor;
import com.project.api_monitor.model.MonitorResult;
import com.project.api_monitor.model.MonitorResultResponse;
import com.project.api_monitor.model.MonitorStatistics;
import com.project.api_monitor.model.MonitorStatus;
import com.project.api_monitor.model.MonitorStatusResponse;
import com.project.api_monitor.repository.MonitorResultRepo;

import lombok.RequiredArgsConstructor;
@RequiredArgsConstructor
@Service
public class MonitorResultService {

	private final MonitorResultRepo repo;
	private final RedisSchedulerService redisSchedulerService;
	
	public MonitorResult saveResult(MonitorResult result) {
		return repo.save(result);
	}
	
	public List<MonitorResult> getResultsByMonitor(Monitor monitor){
		return repo.findByMonitor(monitor);
	}
	
	public MonitorStatistics getStatistics(Monitor monitor){
		List<MonitorResult> results = getResultsByMonitor(monitor);
		int totalChecks = results.size();
		int successfulChecks = 0, failedChecks = 0;
		for(MonitorResult result : results) {
			if(result.getStatus() == MonitorStatus.UP) {
				successfulChecks++;
			} else if(result.getStatus() == MonitorStatus.DOWN) {
				failedChecks++;
			}
		}
		double uptimePerc = 0;
		if(totalChecks != 0) {
			uptimePerc = ((double) successfulChecks / totalChecks) * 100;
		} else {
			uptimePerc = 0;
		}		long totalResponseTime = 0;
		int count = 0;
		Long min = null, max = null;
		
		for(MonitorResult result : results) {
			if(result.getResponseTime() == null)
				continue;
			
			totalResponseTime += result.getResponseTime();
			if(min == null || result.getResponseTime() < min) {
				min = result.getResponseTime();
			}
			if(max == null || result.getResponseTime() > max) {
				max = result.getResponseTime();
			}
			count++;
		}
		double averageResponseTime = 0;
		if(count > 0)
			averageResponseTime = (double) totalResponseTime / count;
		else 
			averageResponseTime = 0;
		
		return MonitorStatistics.builder().monitorId(monitor.getId())
								.totalChecks(totalChecks).successfulChecks(successfulChecks)
								.failedChecks(failedChecks).uptimePercentage(uptimePerc)
								.averageResponseTime(averageResponseTime).minResponseTime(min)
								.maxResponseTime(max).build();
		
	}
	
	public Optional<MonitorResult> getLatestResult(Monitor monitor) {
		return repo.findFirstByMonitorOrderByCheckedAtDesc(monitor);
	}
	
	public Optional<MonitorStatusResponse> getCurrentStatus(Monitor monitor){
		MonitorStatusResponse cached = redisSchedulerService.getCurrentStatus(monitor.getId());		if(cached != null)
			return Optional.of(cached);
		
		Optional<MonitorResult> result = getLatestResult(monitor);
		if(result.isEmpty())
			return Optional.empty();
		
		MonitorResult r = result.get();
		MonitorStatusResponse response = MonitorStatusResponse.builder().monitorId(monitor.getId())
										.status(r.getStatus()).responseTime(r.getResponseTime()).checkedAt(r.getCheckedAt())
										.build();
		redisSchedulerService.setCurrentStatus(monitor.getId(), response);
		return Optional.of(response);
	}
	
	public Page<MonitorResultResponse> getResultsByMonitorPaginated(Monitor monitor, Pageable pageable){
		Page<MonitorResult> page = repo.findByMonitor(monitor, pageable);
		return page.map(result -> MonitorResultResponse.builder().id(result.getId()).status(result.getStatus())
				.statusCode(result.getStatusCode()).responseTime(result.getResponseTime())
				.checkedAt(result.getCheckedAt()).build());
	}
}
