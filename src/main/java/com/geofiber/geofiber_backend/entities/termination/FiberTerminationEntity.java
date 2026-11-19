package com.geofiber.geofiber_backend.entities.termination;

import com.geofiber.geofiber_backend.entities.cable.FiberEntity;
import com.geofiber.geofiber_backend.entities.olt.OltPortEntity;
import com.geofiber.geofiber_backend.entities.serviceport.ServicePortEntity;
import com.geofiber.geofiber_backend.entities.splitter.SplitterPortEntity;
import com.geofiber.geofiber_backend.enums.FiberEndEnum;
import com.geofiber.geofiber_backend.enums.TerminationStatusEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "fiber_termination", uniqueConstraints = {
    @UniqueConstraint(name = "uq_termination_fiber_end", columnNames = {"fiber_id", "end_code"})
})
@Getter
@Setter
@NoArgsConstructor
public class FiberTerminationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fiber_id", nullable = false)
    private FiberEntity fiber;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_code", nullable = false, columnDefinition = "varchar(1)")
    private FiberEndEnum fiberEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TerminationStatusEnum status = TerminationStatusEnum.AVAILABLE;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "olt_port_id", unique = true)
    private OltPortEntity oltPort;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "splitter_port_id", unique = true)
    private SplitterPortEntity splitterPort;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_port_id", unique = true)
    private ServicePortEntity servicePort;
}
