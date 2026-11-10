package com.geofiber.geofiber_backend.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.LineString;

import java.util.List;

@Entity
@Table(name = "cable_tb")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column( nullable = false)
    private String name;
    private String description;
    private Integer fiberCount;
    private Double lengthInMeters;
    @OneToMany(mappedBy = "cable", cascade = CascadeType.ALL,  orphanRemoval = true)
    private List<FiberEntity> fibers;
    private String sourceElementId;
    private String destinationElementId;

    @Column(name = "geom", columnDefinition = "geometry(LineString, 4326)")
    private LineString geom;


}
