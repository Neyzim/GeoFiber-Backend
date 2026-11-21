package com.geofiber.geofiber_backend.entities.cable;

import com.geofiber.geofiber_backend.entities.network.NetworkAssetEntity;
import com.geofiber.geofiber_backend.enums.cable.CableRoleEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.LineString;

@Entity
@Table(name = "network_cable")
@Getter
@Setter
public class CableAssetEntity extends NetworkAssetEntity {
    @Enumerated(EnumType.STRING)
    @Column(name = "cable_role", nullable = false, length = 24)
    private CableRoleEnum role;

    @Column(name = "manufacturer", length = 120)
    private String manufacturer;

    @Column(name = "model", length = 120)
    private String model;

    @Column(name = "fiber_capacity", nullable = false)
    private Integer fiberCapacity;

    @Column(name = "length_meters", insertable = false, updatable = false)
    private Double lengthMeters;

    @Column(name = "geom", columnDefinition = "geometry(LineString,4326)", nullable = false)
    private LineString location;

    public CableAssetEntity() {
        super();
    }
}
