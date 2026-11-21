package com.geofiber.geofiber_backend.dto.readmodel;

import com.geofiber.geofiber_backend.dto.box.BoxResponse;
import com.geofiber.geofiber_backend.dto.connection.ConnectionResponse;
import com.geofiber.geofiber_backend.dto.customer.CustomerResponse;
import com.geofiber.geofiber_backend.dto.serviceport.ServicePortResponse;
import com.geofiber.geofiber_backend.dto.splice.SpliceResponse;
import com.geofiber.geofiber_backend.dto.splitter.SplitterResponse;
import com.geofiber.geofiber_backend.dto.tray.TrayResponse;

import java.util.List;

/** Tudo o que existe numa caixa, para montar o diagrama numa única chamada. */
public record BoxOverviewResponse(
    BoxResponse box,
    List<AttachedCableResponse> cables,
    List<TrayResponse> trays,
    List<SpliceResponse> splices,
    List<SplitterResponse> splitters,
    List<ServicePortResponse> servicePorts,
    List<ConnectionResponse> connections,
    List<CustomerResponse> customers
) {
}
