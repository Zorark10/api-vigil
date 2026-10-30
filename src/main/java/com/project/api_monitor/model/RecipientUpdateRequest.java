package com.project.api_monitor.model;

import jakarta.validation.constraints.NotNull;

public record RecipientUpdateRequest(@NotNull Boolean enabled) {
}
