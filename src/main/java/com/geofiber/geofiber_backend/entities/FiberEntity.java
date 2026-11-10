package com.geofiber.geofiber_backend.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "fiber_tb")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class FiberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String color;
    private String tube;
    @ManyToOne
    @JoinColumn(name = "cable_id")
    private CableEntity cable;

}
