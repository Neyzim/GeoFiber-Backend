package com.geofiber.geofiber_backend.entities.splice;

import com.geofiber.geofiber_backend.entities.box.BoxAssetEntity;
import com.geofiber.geofiber_backend.entities.cable.FiberEntity;
import com.geofiber.geofiber_backend.entities.connection.OpticalConnectionEntity;
import com.geofiber.geofiber_backend.entities.tray.TrayEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "splice", uniqueConstraints = {
    @UniqueConstraint(name = "uq_splice_input_fiber", columnNames = "input_fiber_id"),
    @UniqueConstraint(name = "uq_splice_output_fiber", columnNames = "output_fiber_id"),
    @UniqueConstraint(name = "uq_splice_tray_position", columnNames = {"tray_id", "position_number"})
})
@Getter
@Setter
@NoArgsConstructor
public class SpliceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "attenuation_db")
    private Double attenuationDb;

    @Column(name = "position_number", nullable = false)
    private Integer positionNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "box_id", nullable = false)
    private BoxAssetEntity box;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "input_fiber_id", nullable = false)
    private FiberEntity inputFiber;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "output_fiber_id", nullable = false)
    private FiberEntity outputFiber;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "connection_id", nullable = false, unique = true)
    private OpticalConnectionEntity connection;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tray_id", nullable = false)
    private TrayEntity tray;
}
