package com.geofiber.geofiber_backend.entities.tray;

import com.geofiber.geofiber_backend.entities.box.BoxAssetEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "tray", uniqueConstraints = {
    @UniqueConstraint(name = "uq_tray_box_number", columnNames = {"box_id", "tray_number"})
})
@Getter
@Setter
@NoArgsConstructor
public class TrayEntity {
    //Bandeja
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "box_id", nullable = false)
    private BoxAssetEntity box;

    @Column(name = "tray_number", nullable = false)
    private Integer trayNumber;

    @Column(nullable = false)
    private Integer capacity;
}
