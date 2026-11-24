package com.geofiber.geofiber_backend.entities.network;

import com.geofiber.geofiber_backend.enums.InfrastructureStatusEnum;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
public abstract class NetworkAssetEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_status", nullable = false, length = 20)
    private InfrastructureStatusEnum usageStatus = InfrastructureStatusEnum.PLANNED;

    /** Usuário que criou o elemento. Nulo só em linhas anteriores à V8 (ver BootstrapAdminRunner). */
    @Column(name = "created_by")
    private UUID createdBy;

    @PrePersist
    protected void applyDefaults() {
        if (usageStatus == null) {
            usageStatus = InfrastructureStatusEnum.PLANNED;
        }
    }
}
