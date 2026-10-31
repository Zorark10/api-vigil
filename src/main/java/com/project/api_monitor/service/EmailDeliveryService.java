package com.project.api_monitor.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Delivery seam used by NotificationService. Delegates to EmailJsClient
 * (EmailJS REST API); failures are reported as false instead of thrown, so a
 * delivery outage can never interrupt monitoring, Kafka processing or
 * incident creation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailDeliveryService {

	private final EmailJsClient client;

	public boolean send(String to, String subject, String text) {
		return client.send(to, subject, text);
	}
}
