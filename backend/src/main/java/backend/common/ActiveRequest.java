package backend.common;

import jakarta.validation.constraints.NotNull;

public record ActiveRequest(@NotNull Boolean active) {}