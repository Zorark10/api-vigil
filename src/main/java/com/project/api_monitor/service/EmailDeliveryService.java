package com.project.api_monitor.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Thin wrapper around JavaMailSender. Delivery problems are logged and
 * reported to the caller (false) instead of thrown, so an SMTP outage can
 * never interrupt monitoring, Kafka processing or incident creation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailDeliveryService {

	private final JavaMailSender mailSender;

	@Value("${spring.mail.username:}")
	private String from;

	public boolean send(String to, String subject, String text) {
		try {
			SimpleMailMessage message = new SimpleMailMessage();
			if (from != null && !from.isBlank()) {
				message.setFrom(from);
			}
			message.setTo(to);
			message.setSubject(subject);
			message.setText(text);
			mailSender.send(message);
			return true;
		} catch (Exception ex) {
			log.error("Failed to send notification email to {}: {}", to, ex.getMessage());
			return false;
		}
	}
}
