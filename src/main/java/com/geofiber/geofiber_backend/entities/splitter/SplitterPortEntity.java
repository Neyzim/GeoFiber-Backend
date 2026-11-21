package com.geofiber.geofiber_backend.entities.splitter;

import com.geofiber.geofiber_backend.enums.PortDirectionEnum;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Porta de um splitter. A fibra ligada à porta é registrada em
 * {@code FiberTerminationEntity.splitterPort}; uma saída pode, em vez disso, alimentar uma
 * porta de atendimento ({@code ServicePortEntity.splitterPort}).
 */
@Entity
@Table(name = "splitter_port", uniqueConstraints = {
    @UniqueConstraint(name = "uq_splitter_port_number", columnNames = {"splitter_id", "port_number"})
})
@Getter
@Setter
@NoArgsConstructor
public class SplitterPortEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "splitter_id", nullable = false)
    private SplitterEntity splitter;

    @Column(name = "port_number", nullable = false)
    private Integer portNumber;

    @Column(name = "attenuation_db")
    private Double attenuationDb;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PortStatusEnum status = PortStatusEnum.FREE;

    @Enumerated(EnumType.STRING)
    @Column(name = "port_direction", nullable = false, length = 8)
    private PortDirectionEnum direction;
}
