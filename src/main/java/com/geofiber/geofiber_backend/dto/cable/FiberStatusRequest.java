package com.geofiber.geofiber_backend.dto.cable;

import com.geofiber.geofiber_backend.enums.FiberStatusEnum;
import jakarta.validation.constraints.NotNull;

/** Aceita AVAILABLE, BROKEN ou RESERVED; OCCUPIED é definido pelas ligações. */
public record FiberStatusRequest(@NotNull FiberStatusEnum status) {
}
