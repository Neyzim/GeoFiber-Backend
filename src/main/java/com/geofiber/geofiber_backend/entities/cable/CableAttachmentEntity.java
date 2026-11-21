package com.geofiber.geofiber_backend.entities.cable;

import com.geofiber.geofiber_backend.entities.box.BoxAssetEntity;
import com.geofiber.geofiber_backend.entities.olt.OltAssetEntity;
import com.geofiber.geofiber_backend.enums.cable.CableAttachmentRoleEnum;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Um elemento (caixa ou OLT) por onde o cabo passa; exatamente um de {@code box}/{@code olt}. */
@Entity
@Table(name = "cable_attachment")
@Getter
@Setter
@NoArgsConstructor
public class CableAttachmentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cable_id", nullable = false)
    private CableAssetEntity cable;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CableAttachmentRoleEnum role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "box_id")
    private BoxAssetEntity box;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "olt_id")
    private OltAssetEntity olt;
}
