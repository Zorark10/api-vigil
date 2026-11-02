package com.project.api_monitor.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Monitor {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	// Workspace ownership: resolved from the authenticated JWT principal on
	// the server side, never accepted from the request body. Not serialized
	// (the registration shape of the API keeps the user bytes out of every
	// monitor payload and prevents the password hash from leaking).
	@JsonIgnore
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false)
	private AppUser owner;

	@NotBlank
	private String name;
	@NotBlank
	private String url;

	@Positive
	private Integer interval;
	@Positive
	private Integer timeout;
	private boolean enabled;
	private LocalDateTime createdAt;

	@Enumerated(EnumType.STRING)
	@NotNull
	private HttpMethod httpMethod;
}
