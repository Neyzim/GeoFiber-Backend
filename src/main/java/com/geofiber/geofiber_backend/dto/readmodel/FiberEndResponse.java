package com.geofiber.geofiber_backend.dto.readmodel;

import com.geofiber.geofiber_backend.enums.FiberEndEnum;
import com.geofiber.geofiber_backend.enums.TerminationStatusEnum;

/** Uma ponta da fibra; {@code link} é nulo quando está livre. */
public record FiberEndResponse(FiberEndEnum end, TerminationStatusEnum status, FiberEndLinkResponse link) {
}
