package com.geofiber.geofiber_backend.entities.serviceport;

import com.geofiber.geofiber_backend.entities.box.BoxAssetEntity;
import com.geofiber.geofiber_backend.entities.splitter.SplitterPortEntity;
import com.geofiber.geofiber_backend.enums.PortStatusEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Porta de atendimento de uma CTO, onde o drop do cliente é conectado. A fonte óptica é uma
 * saída de splitter ({@link #splitterPort}) ou uma ponta de fibra
 * ({@code FiberTerminationEntity.servicePort}), nunca as duas.
 */
@Entity
@Table(name = "service_port", uniqueConstraints = {
    @UniqueConstraint(name = "uq_service_port_box_number", columnNames = {"box_id", "port_number"})
})
@Getter
@Setter
@NoArgsConstructor
public class ServicePortEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "box_id", nullable = false)
    private BoxAssetEntity box;

    @Column(name = "port_number", nullable = false)
    private Integer portNumber;

    @Column(length = 80)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PortStatusEnum status = PortStatusEnum.FREE;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "splitter_port_id", unique = true)
    private SplitterPortEntity splitterPort;
}
