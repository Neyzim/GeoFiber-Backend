package com.geofiber.geofiber_backend.entities;

import com.geofiber.geofiber_backend.entities.cable.CableEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "fiber_tb")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class FiberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String color;
    private String tube;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cable_model_id", nullable = false)
    private CableEntity cable;

}
