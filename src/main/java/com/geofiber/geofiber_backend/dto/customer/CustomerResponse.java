package com.geofiber.geofiber_backend.dto.customer;

import com.geofiber.geofiber_backend.enums.customer.CustomerStatusEnum;
import com.geofiber.geofiber_backend.dto.geo.CoordinateDto;

import java.util.UUID;

public record CustomerResponse(
    UUID id,
    String name,
    String contractCode,
    String address,
    CoordinateDto location,
    CustomerStatusEnum status,
    UUID servicePortId,
    UUID boxId
) {
}
