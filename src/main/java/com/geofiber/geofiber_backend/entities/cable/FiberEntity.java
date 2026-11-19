package com.geofiber.geofiber_backend.entities.cable;

import com.geofiber.geofiber_backend.enums.OpticalColorEnum;
import com.geofiber.geofiber_backend.enums.FiberStatusEnum;
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

@Entity
@Table(name = "fiber", uniqueConstraints = {
    @UniqueConstraint(name = "uq_fiber_absolute_number", columnNames = {"cable_id", "absolute_number"}),
    @UniqueConstraint(name = "uq_fiber_tube_number", columnNames = {"tube_id", "number_in_tube"})
})
@Getter
@Setter
@NoArgsConstructor
public class FiberEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cable_id", nullable = false)
    private CableAssetEntity cable;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tube_id", nullable = false)
    private LooseTubeEntity tube;

    @Column(name = "number_in_tube", nullable = false)
    private Integer numberInTube;

    @Column(name = "absolute_number", nullable = false)
    private Integer absoluteNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OpticalColorEnum color;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FiberStatusEnum status = FiberStatusEnum.AVAILABLE;
}
