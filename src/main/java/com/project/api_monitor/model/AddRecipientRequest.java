package com.project.api_monitor.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AddRecipientRequest(@NotBlank @Email String email) {
}
