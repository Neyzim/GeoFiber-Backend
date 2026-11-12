package com.geofiber.geofiber_backend.entities.cable;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "cable_models")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CableModelEntity {
    //* Esta Entidade serve para o cadastro de um novo tipo de cabo via Front End*//
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;
    private String manufacturer;
    private String model;

    private String constructionType; //AS, Drop etc

    private Integer fiberCount;
    private String description;
}
