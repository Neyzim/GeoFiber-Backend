    package com.geofiber.geofiber_backend.entities.cable;

    import com.geofiber.geofiber_backend.entities.FiberEntity;
    import com.geofiber.geofiber_backend.enums.CableApplicationEnum;
    import com.geofiber.geofiber_backend.enums.CableStatus;
    import jakarta.persistence.*;
    import lombok.AllArgsConstructor;
    import lombok.Getter;
    import lombok.NoArgsConstructor;
    import lombok.Setter;
    import org.locationtech.jts.geom.LineString;

    import java.util.ArrayList;
    import java.util.List;
    import java.util.UUID;

    @Entity
    @Table(name = "cable_tb")
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public class CableEntity {
        // Esta entidade represnta o PIN do cabo no mapa do frontend
        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;

        @ManyToOne
        @JoinColumn(name = "cable_model_id")
        private CableModelEntity cableModel;
        private String info;

        @Enumerated(EnumType.STRING)
        private CableApplicationEnum cableApplicationEnum; //backbone, distribuição etc
        @Enumerated(EnumType.STRING)
        private CableStatus cableStatus;

        private Double lengthInMeters;
        @OneToMany(mappedBy = "cable", cascade = CascadeType.ALL,  orphanRemoval = true)
        private List<FiberEntity> fibers = new ArrayList<>();

        @Column(name = "geom", columnDefinition = "geometry(LineString, 4326)")
        private LineString geom;


    }
