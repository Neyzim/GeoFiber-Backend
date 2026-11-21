package com.geofiber.geofiber_backend.repositories.customer;

import com.geofiber.geofiber_backend.entities.customer.CustomerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<CustomerEntity, UUID> {
    Optional<CustomerEntity> findByServicePort_Id(UUID servicePortId);

    List<CustomerEntity> findAllByServicePort_IdIn(Collection<UUID> servicePortIds);
}
