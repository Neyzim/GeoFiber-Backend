package com.geofiber.geofiber_backend.dto.customer;

import com.geofiber.geofiber_backend.enums.customer.CustomerStatusEnum;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CustomerHistoryResponse(CustomerStatusEnum status, UUID servicePortId, OffsetDateTime changedAt) {
}
