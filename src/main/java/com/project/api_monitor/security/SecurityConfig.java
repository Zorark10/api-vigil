package com.project.api_monitor.security;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.project.api_monitor.exception.ErrorResponse;
import com.project.api_monitor.repository.AppUserRepo;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

/**
 * Stateless JWT security: only the login endpoint is public; everything else
 * requires a valid Bearer token. Bad credentials give 401, missing
 * permissions give 403 — both as JSON bodies.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public UserDetailsService userDetailsService(AppUserRepo repo) {
		return username -> repo.findByUsername(username)
				.map(user -> User.withUsername(user.getUsername())
						.password(user.getPasswordHash())
						.roles(user.getRole())
						.build())
				.orElseThrow(() -> new UsernameNotFoundException(username));
	}

	@Bean
	public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource(
			@Value("${app.cors.allowed-origins:http://localhost:5173}") String allowedOrigins) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
		config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(Arrays.asList("Content-Type", "Authorization"));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", config);
		return source;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
			CorsConfigurationSource corsConfigurationSource, ObjectMapper objectMapper) throws Exception {
		JwtDecoder jwtDecoder = jwtService.jwtDecoder();

		http
				.csrf(csrf -> csrf.disable())
				.cors(cors -> cors.configurationSource(corsConfigurationSource))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/auth/setup-status").permitAll()
						.requestMatchers("/error").permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint((request, response, ex) ->
								writeError(response, 401, "Authentication required", objectMapper))
						.accessDeniedHandler((request, response, ex) ->
								writeError(response, 403, "Access denied", objectMapper)))
				.oauth2ResourceServer(resourceServer -> resourceServer
						.jwt(jwt -> jwt.decoder(jwtDecoder))
						.authenticationEntryPoint((request, response, ex) ->
								writeError(response, 401, bearerMessage(ex), objectMapper))
						.accessDeniedHandler((request, response, ex) ->
								writeError(response, 403, "Access denied", objectMapper)));

		return http.build();
	}

	private String bearerMessage(AuthenticationException ex) {
		return ex instanceof InvalidBearerTokenException ? "Invalid or expired token" : "Authentication required";
	}

	private void writeError(HttpServletResponse response, int status, String message, ObjectMapper mapper)
			throws java.io.IOException {
		response.setStatus(status);
		response.setContentType("application/json");
		mapper.writeValue(response.getWriter(),
				new ErrorResponse(status, message, LocalDateTime.now()));
	}
}
