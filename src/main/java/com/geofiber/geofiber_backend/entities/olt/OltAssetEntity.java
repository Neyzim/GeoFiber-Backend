package com.geofiber.geofiber_backend.entities.olt;

import com.geofiber.geofiber_backend.entities.network.NetworkAssetEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "network_olt")
@Getter
@Setter
public class OltAssetEntity extends NetworkAssetEntity {
    @Column(length = 120)
    private String model;

    @Column(name = "geom", columnDefinition = "geometry(Point,4326)", nullable = false)
    private Point location;

    public OltAssetEntity() {
        super();
    }
}
