package com.geofiber.geofiber_backend.entities.box;

import com.geofiber.geofiber_backend.enums.BoxStatusEnum;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.Point;

import java.util.UUID;

@Entity
@Table(name = "box_tb")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BoxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne
    private BoxModelEntity boxModelEntity;
    private String name;
    private String description;
    private String address;
    @Enumerated(EnumType.STRING)
    private BoxStatusEnum boxStatusEnum;
    private Point geom;
}
