package com.project.api_monitor.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.project.api_monitor.model.MonitorStatus;
import com.project.api_monitor.model.MonitorStatusResponse;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class RedisSchedulerService {
	
	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	
	public void setNextCheck(Integer monitorId, long nextCheckTime) {
		
		String key = "monitor:" + monitorId + ":nextCheck";
		redisTemplate.opsForValue().set(key, String.valueOf(nextCheckTime));
	}
	
	public Long getNextCheck(Integer monitorId) {
		String key = "monitor:" + monitorId + ":nextCheck";
		String value = redisTemplate.opsForValue().get(key);

		if(value == null)
			return null;

		return Long.valueOf(value);
	}

	/** Drops the scheduling and cached-status entries of a deleted monitor.
	 *  Best-effort: callers roll back nothing for a cache outage, so a Redis
	 *  failure here must be swallowed (stale keys are harmless leftovers —
	 *  they key a monitor id that no longer exists and simply expire). */
	public void deleteMonitorCache(Integer monitorId) {
		try {
			redisTemplate.delete("monitor:" + monitorId + ":nextCheck");
			redisTemplate.delete("monitor:" + monitorId + ":status");
		} catch (RuntimeException ex) {
			org.slf4j.LoggerFactory.getLogger(getClass())
					.warn("Could not clear Redis cache entries for deleted monitor {}: {}",
							monitorId, ex.toString());
		}
	}
	
	public void setCurrentStatus(Integer monitorId, MonitorStatusResponse response) {
		String key = "monitor:" + monitorId + ":status";
		String json = objectMapper.writeValueAsString(response);
		redisTemplate.opsForValue().set(key, json);
	}
	
	public MonitorStatusResponse getCurrentStatus(Integer monitorId) {
		String key = "monitor:" + monitorId + ":status";
		String value = redisTemplate.opsForValue().get(key);
		MonitorStatusResponse response = objectMapper.readValue(value, MonitorStatusResponse.class);
		
		if(value == null)
			return null;
		
		return response;
		}
}
