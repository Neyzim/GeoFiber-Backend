package com.geofiber.geofiber_backend.entities.olt;

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
 * Porta PON de uma OLT. A fibra ligada à porta é registrada em
 * {@code FiberTerminationEntity.oltPort}, que é a única fonte da verdade dessa ligação.
 */
@Entity
@Table(name = "olt_port", uniqueConstraints = {
    @UniqueConstraint(name = "uq_olt_port_position", columnNames = {"olt_id", "slot_number", "port_number"})
})
@Getter
@Setter
@NoArgsConstructor
public class OltPortEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "olt_id", nullable = false)
    private OltAssetEntity olt;

    @Column(name = "slot_number", nullable = false)
    private Integer slotNumber;

    @Column(name = "port_number", nullable = false)
    private Integer portNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PortStatusEnum status = PortStatusEnum.FREE;
}
