package com.geofiber.geofiber_backend.entities.connection;

import com.geofiber.geofiber_backend.entities.box.BoxAssetEntity;
import com.geofiber.geofiber_backend.entities.termination.FiberTerminationEntity;
import com.geofiber.geofiber_backend.enums.OpticalConnectionTypeEnum;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Ligação física entre duas pontas de fibra dentro de uma caixa: fusão ou conector.
 * O banco exige termination_a_id < termination_b_id; use {@code FiberTerminationService.connect}
 * para criar conexões com a ordem correta.
 */
@Entity
@Table(name = "optical_connection")
@Getter
@Setter
@NoArgsConstructor
public class OpticalConnectionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_type", nullable = false, length = 16)
    private OpticalConnectionTypeEnum type = OpticalConnectionTypeEnum.FUSION;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "box_id", nullable = false)
    private BoxAssetEntity box;

    @Column(name = "attenuation_db")
    private Double attenuationDb;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "termination_a_id", nullable = false, unique = true)
    private FiberTerminationEntity terminationA;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "termination_b_id", nullable = false, unique = true)
    private FiberTerminationEntity terminationB;
}
