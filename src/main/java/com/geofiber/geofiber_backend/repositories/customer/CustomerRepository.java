package com.geofiber.geofiber_backend.repositories.customer;

import com.geofiber.geofiber_backend.entities.customer.CustomerEntity;
import com.geofiber.geofiber_backend.enums.customer.CustomerStatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<CustomerEntity, UUID> {
    Optional<CustomerEntity> findByServicePort_Id(UUID servicePortId);

    List<CustomerEntity> findAllByServicePort_IdIn(Collection<UUID> servicePortIds);

    List<CustomerEntity> findAllByServicePort_Box_IdOrderByNameAsc(UUID boxId);

    /** Clientes que ainda podem ter sinal: com porta de atendimento e fora do cancelamento. */
    List<CustomerEntity> findAllByStatusNotAndServicePortIsNotNull(CustomerStatusEnum status);
}
