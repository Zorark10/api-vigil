package com.project.api_monitor.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.project.api_monitor.model.IncidentEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Consumes incident transition events (fired exactly once per OPEN / RESOLVED
 * by IncidentService) and passes them to the notification pipeline. The
 * listener never throws: a malformed payload is logged and dropped instead of
 * triggering Kafka redelivery loops.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IncidentEventConsumer {

	private final ObjectMapper objectMapper;
	private final NotificationService notificationService;

	@KafkaListener(topics = "incident-events",
			groupId = "incident-notification-group")
	public void receiveIncidentEvent(String json) {
		try {
			IncidentEvent event = objectMapper.readValue(json, IncidentEvent.class);
			notificationService.handleIncidentEvent(event);
		} catch (Exception ex) {
			log.error("Could not process incident event payload: {}", ex.getMessage());
		}
	}
}
