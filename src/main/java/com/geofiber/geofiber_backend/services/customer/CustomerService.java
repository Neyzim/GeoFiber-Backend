package com.geofiber.geofiber_backend.services.customer;

import com.geofiber.geofiber_backend.dto.customer.CustomerHistoryResponse;
import com.geofiber.geofiber_backend.dto.customer.CustomerRequest;
import com.geofiber.geofiber_backend.dto.customer.CustomerResponse;
import com.geofiber.geofiber_backend.entities.customer.CustomerEntity;
import com.geofiber.geofiber_backend.entities.customer.CustomerStatusHistoryEntity;
import com.geofiber.geofiber_backend.entities.serviceport.ServicePortEntity;
import com.geofiber.geofiber_backend.enums.PortStatusEnum;
import com.geofiber.geofiber_backend.enums.customer.CustomerStatusEnum;
import com.geofiber.geofiber_backend.repositories.customer.CustomerRepository;
import com.geofiber.geofiber_backend.repositories.customer.CustomerStatusHistoryRepository;
import com.geofiber.geofiber_backend.repositories.serviceport.ServicePortRepository;
import com.geofiber.geofiber_backend.repositories.termination.FiberTerminationRepository;
import com.geofiber.geofiber_backend.services.geo.GeometryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.geofiber.geofiber_backend.services.common.ServiceErrors.conflict;
import static com.geofiber.geofiber_backend.services.common.ServiceErrors.notFound;

/**
 * Cliente atendido por uma porta de atendimento de CTO. Bloqueado mantém a porta; cancelado
 * libera a porta e continua cadastrado. Toda mudança de situação ou de porta vai para o histórico.
 */
@Service
@Transactional
public class CustomerService {
    private final CustomerRepository customerRepository;
    private final CustomerStatusHistoryRepository historyRepository;
    private final ServicePortRepository servicePortRepository;
    private final FiberTerminationRepository terminationRepository;

    public CustomerService(
        CustomerRepository customerRepository,
        CustomerStatusHistoryRepository historyRepository,
        ServicePortRepository servicePortRepository,
        FiberTerminationRepository terminationRepository
    ) {
        this.customerRepository = customerRepository;
        this.historyRepository = historyRepository;
        this.servicePortRepository = servicePortRepository;
        this.terminationRepository = terminationRepository;
    }

    public CustomerResponse create(CustomerRequest request) {
        CustomerEntity customer = new CustomerEntity();
        apply(customer, request, request.status() == null ? CustomerStatusEnum.ACTIVE : request.status());
        customerRepository.save(customer);
        recordHistory(customer);
        customerRepository.flush();
        return toResponse(customer);
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> findAll() {
        return customerRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponse findById(UUID id) {
        return toResponse(getCustomer(id));
    }

    @Transactional(readOnly = true)
    public List<CustomerHistoryResponse> findHistory(UUID id) {
        getCustomer(id);
        return historyRepository.findAllByCustomer_IdOrderByChangedAtAsc(id).stream()
            .map(entry -> new CustomerHistoryResponse(
                entry.getStatus(),
                entry.getServicePort() == null ? null : entry.getServicePort().getId(),
                entry.getChangedAt()))
            .toList();
    }

    public CustomerResponse update(UUID id, CustomerRequest request) {
        CustomerEntity customer = getCustomer(id);
        CustomerStatusEnum previousStatus = customer.getStatus();
        ServicePortEntity previousPort = customer.getServicePort();
        apply(customer, request, request.status() == null ? previousStatus : request.status());
        if (customer.getStatus() != previousStatus || customer.getServicePort() != previousPort) {
            recordHistory(customer);
        }
        customerRepository.flush();
        return toResponse(customer);
    }

    /** Remove um cadastro feito por engano, com o histórico. Para encerrar um contrato, use CANCELLED. */
    public void delete(UUID id) {
        CustomerEntity customer = getCustomer(id);
        release(customer.getServicePort());
        customerRepository.delete(customer);
    }

    private CustomerEntity getCustomer(UUID id) {
        return customerRepository.findById(id).orElseThrow(notFound("Customer"));
    }

    private void apply(CustomerEntity customer, CustomerRequest request, CustomerStatusEnum status) {
        customer.setName(request.name());
        customer.setContractCode(request.contractCode());
        customer.setAddress(request.address());
        customer.setLocation(GeometryMapper.toPoint(request.location()));
        customer.setStatus(status);

        ServicePortEntity current = customer.getServicePort();
        UUID currentId = current == null ? null : current.getId();
        UUID requestedId = request.servicePortId();
        if (status == CustomerStatusEnum.CANCELLED) {
            // Cancelar libera a porta atual; pedir outra porta para um cliente cancelado é erro.
            if (requestedId != null && !requestedId.equals(currentId)) {
                throw conflict("A cancelled customer cannot use a service port");
            }
            requestedId = null;
        }
        if (Objects.equals(currentId, requestedId)) {
            return;
        }
        ServicePortEntity selected = requestedId == null ? null : getAvailablePort(requestedId);
        release(current);
        if (selected != null) {
            selected.setStatus(PortStatusEnum.OCCUPIED);
        }
        customer.setServicePort(selected);
    }

    /** A porta deixada pelo cliente fica livre, a menos que esteja marcada com defeito. */
    private static void release(ServicePortEntity port) {
        if (port != null && port.getStatus() != PortStatusEnum.FAULTY) {
            port.setStatus(PortStatusEnum.FREE);
        }
    }

    private void recordHistory(CustomerEntity customer) {
        CustomerStatusHistoryEntity entry = new CustomerStatusHistoryEntity();
        entry.setCustomer(customer);
        entry.setStatus(customer.getStatus());
        entry.setServicePort(customer.getServicePort());
        entry.setChangedAt(OffsetDateTime.now());
        historyRepository.save(entry);
    }

    private ServicePortEntity getAvailablePort(UUID id) {
        ServicePortEntity port = servicePortRepository.findById(id).orElseThrow(notFound("Service port"));
        if (port.getStatus() != PortStatusEnum.FREE || customerRepository.findByServicePort_Id(id).isPresent()) {
            throw conflict("Service port is not available");
        }
        if (port.getSplitterPort() == null && terminationRepository.findByServicePort_Id(id).isEmpty()) {
            throw conflict("Service port has no optical source");
        }
        return port;
    }

    private CustomerResponse toResponse(CustomerEntity customer) {
        ServicePortEntity port = customer.getServicePort();
        return new CustomerResponse(
            customer.getId(), customer.getName(), customer.getContractCode(),
            customer.getAddress(), GeometryMapper.fromPoint(customer.getLocation()),
            customer.getStatus(),
            port == null ? null : port.getId(),
            port == null ? null : port.getBox().getId());
    }
}
