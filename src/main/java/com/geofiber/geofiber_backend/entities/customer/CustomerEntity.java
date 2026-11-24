package com.geofiber.geofiber_backend.entities.customer;

import com.geofiber.geofiber_backend.entities.serviceport.ServicePortEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import com.geofiber.geofiber_backend.enums.customer.CustomerStatusEnum;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.Point;

import java.util.UUID;

@Entity
@Table(name = "customer")
@Getter
@Setter
@NoArgsConstructor
public class CustomerEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "contract_code", nullable = false, unique = true, length = 80)
    private String contractCode;

    @Column(length = 500)
    private String address;

    @Column(name = "geom", columnDefinition = "geometry(Point,4326)", nullable = false)
    private Point location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CustomerStatusEnum status = CustomerStatusEnum.ACTIVE;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_port_id", unique = true)
    private ServicePortEntity servicePort;

    /** Usuário que cadastrou o cliente. Nulo só em linhas anteriores à V8. */
    @Column(name = "created_by")
    private UUID createdBy;
}
