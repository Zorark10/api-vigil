package com.project.api_monitor.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import javax.crypto.spec.SecretKeySpec;

/**
 * Issues and validates HS256 JWTs. The signing secret comes from the
 * JWT_SECRET environment variable and never falls back to a default value.
 */
@Component
public class JwtService {

	private final SecretKey key;
	private final String secret;
	private final Duration ttl;

	public JwtService(@Value("${app.jwt.secret:}") String secret,
			@Value("${app.jwt.expiration:PT1H}") Duration ttl) {
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalStateException(
					"Set the JWT_SECRET environment variable to a secret of at least 32 characters "
							+ "(for example: openssl rand -base64 48).");
		}
		this.secret = secret;
		this.ttl = ttl;
		this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	public String issueToken(String subject) {
		return issueToken(subject, Instant.now());
	}

	/** Visible for tests that need an already-expired token. */
	public String issueToken(String subject, Instant issuedAt) {
		JWK jwk = new OctetSequenceKey.Builder(key).keyUse(KeyUse.SIGNATURE)
				.algorithm(JWSAlgorithm.HS256).build();
		JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(jwk));
		JwtEncoder encoder = new NimbusJwtEncoder(jwkSource);

		var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
				.subject(subject)
				.issuedAt(issuedAt)
				.expiresAt(issuedAt.plus(ttl))
				.build();

		Jwt token = encoder.encode(JwtEncoderParameters
				.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims));
		return token.getTokenValue();
	}

	public JwtDecoder jwtDecoder() {
		return NimbusJwtDecoder.withSecretKey(key).build();
	}

	public String secret() {
		return secret;
	}
}
