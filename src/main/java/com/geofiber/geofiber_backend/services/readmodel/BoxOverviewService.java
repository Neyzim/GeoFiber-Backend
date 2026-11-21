package com.geofiber.geofiber_backend.services.readmodel;

import com.geofiber.geofiber_backend.dto.readmodel.BoxOverviewResponse;
import com.geofiber.geofiber_backend.services.box.BoxService;
import com.geofiber.geofiber_backend.services.connection.ConnectionService;
import com.geofiber.geofiber_backend.services.customer.CustomerService;
import com.geofiber.geofiber_backend.services.serviceport.ServicePortService;
import com.geofiber.geofiber_backend.services.splice.SpliceService;
import com.geofiber.geofiber_backend.services.splitter.SplitterService;
import com.geofiber.geofiber_backend.services.tray.TrayService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Visão geral de uma caixa: reúne numa leitura consistente tudo o que os endpoints da caixa listam. */
@Service
@Transactional(readOnly = true)
public class BoxOverviewService {
    private final BoxService boxService;
    private final CableReadService cableReadService;
    private final TrayService trayService;
    private final SpliceService spliceService;
    private final SplitterService splitterService;
    private final ServicePortService servicePortService;
    private final ConnectionService connectionService;
    private final CustomerService customerService;

    public BoxOverviewService(
        BoxService boxService,
        CableReadService cableReadService,
        TrayService trayService,
        SpliceService spliceService,
        SplitterService splitterService,
        ServicePortService servicePortService,
        ConnectionService connectionService,
        CustomerService customerService
    ) {
        this.boxService = boxService;
        this.cableReadService = cableReadService;
        this.trayService = trayService;
        this.spliceService = spliceService;
        this.splitterService = splitterService;
        this.servicePortService = servicePortService;
        this.connectionService = connectionService;
        this.customerService = customerService;
    }

    public BoxOverviewResponse findByBox(UUID boxId) {
        return new BoxOverviewResponse(
            boxService.findById(boxId),
            cableReadService.findByBox(boxId),
            trayService.findByBox(boxId),
            spliceService.findByBox(boxId),
            splitterService.findByBox(boxId),
            servicePortService.findByBox(boxId),
            connectionService.findByBox(boxId),
            customerService.findByBox(boxId));
    }
}
