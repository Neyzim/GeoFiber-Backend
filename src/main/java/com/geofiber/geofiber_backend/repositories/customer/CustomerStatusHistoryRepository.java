package com.geofiber.geofiber_backend.repositories.customer;

import com.geofiber.geofiber_backend.entities.customer.CustomerStatusHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomerStatusHistoryRepository extends JpaRepository<CustomerStatusHistoryEntity, UUID> {
    List<CustomerStatusHistoryEntity> findAllByCustomer_IdOrderByChangedAtAsc(UUID customerId);
}
