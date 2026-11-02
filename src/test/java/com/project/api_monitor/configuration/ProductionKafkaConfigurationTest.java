package com.project.api_monitor.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class ProductionKafkaConfigurationTest {

	@Test
	void productionKafkaClientsUseAivenPemTrustAndScramTls() throws IOException {
		Properties production = load("application-production.properties");

		// spring.kafka.properties is Spring Boot's shared client-property map,
		// so these trust and protocol settings apply to producer, consumer and admin.
		assertEquals("SASL_SSL", production.getProperty("spring.kafka.properties.security.protocol"));
		assertEquals("SCRAM-SHA-256", production.getProperty("spring.kafka.properties.sasl.mechanism"));
		assertEquals("PEM", production.getProperty("spring.kafka.properties.ssl.truststore.type"));
		assertEquals("${KAFKA_CA_CERT}",
				production.getProperty("spring.kafka.properties.ssl.truststore.certificates"));

		assertTrue(production.getProperty("spring.kafka.properties.sasl.jaas.config")
				.contains("username=\"${KAFKA_USERNAME}\" password=\"${KAFKA_PASSWORD}\""));
		assertEquals("SCRAM-SHA-256", production.getProperty("spring.kafka.consumer.properties.sasl.mechanism"));
		assertEquals("SCRAM-SHA-256", production.getProperty("spring.kafka.admin.properties.sasl.mechanism"));
		assertFalse(production.stringPropertyNames().stream()
				.anyMatch(key -> key.endsWith("ssl.endpoint.identification.algorithm")));
	}

	@Test
	void productionBindsToRendersAssignedPortOnAllInterfaces() throws IOException {
		Properties production = load("application-production.properties");

		assertEquals("${PORT:8080}", production.getProperty("server.port"));
		assertEquals("0.0.0.0", production.getProperty("server.address"));
	}

	@Test
	void pemTrustConfigurationIsProductionOnly() throws IOException {
		Properties common = load("application.properties");
		Properties production = load("application-production.properties");

		assertFalse(common.containsKey("spring.kafka.properties.ssl.truststore.type"));
		assertNotNull(production.getProperty("spring.kafka.properties.ssl.truststore.type"));
		assertNotNull(production.getProperty("spring.kafka.properties.ssl.truststore.certificates"));
	}

	private Properties load(String resource) throws IOException {
		Properties properties = new Properties();
		try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
			assertNotNull(input, "Missing configuration resource " + resource);
			properties.load(input);
		}
		return properties;
	}
}
