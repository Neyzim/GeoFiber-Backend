package com.geofiber.geofiber_backend.entities.box;

import com.geofiber.geofiber_backend.enums.BoxType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "box_models")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BoxModelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String name;
    private Integer capacity;
    @Enumerated(EnumType.STRING)
    private BoxType type;
    private String manufacturer;
    private String model;

}
