package com.geofiber.geofiber_backend.entities.box;

import com.geofiber.geofiber_backend.entities.network.NetworkAssetEntity;
import com.geofiber.geofiber_backend.enums.box.BoxTypeEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "network_box")
@Getter
@Setter
public class BoxAssetEntity extends NetworkAssetEntity {


    @Enumerated(EnumType.STRING)
    @Column(name = "box_type", nullable = false, length = 16)
    private BoxTypeEnum type;

    @Column(name = "power_capacity")
    private Integer powerCapacity;

    @Column(name = "tray_capacity")
    private Integer trayCapacity;

    @Column(length = 120)
    private String manufacturer;

    @Column(length = 120)
    private String model;

    @Column(length = 500)
    private String address;

    @Column(name = "geom", columnDefinition = "geometry(Point,4326)", nullable = false)
    private Point location;

    public BoxAssetEntity() {
        super();
    }
}
