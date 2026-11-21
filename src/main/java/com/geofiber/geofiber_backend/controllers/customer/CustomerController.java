package com.geofiber.geofiber_backend.controllers.customer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.geofiber.geofiber_backend.dto.customer.CustomerHistoryResponse;
import com.geofiber.geofiber_backend.dto.customer.CustomerRequest;
import com.geofiber.geofiber_backend.dto.customer.CustomerResponse;
import com.geofiber.geofiber_backend.services.customer.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Clientes", description = "Clientes atendidos por uma porta de atendimento de CTO.")
@RequestMapping("/api/v1/customers")
public class CustomerController {
    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    @Operation(summary = "Cadastra um cliente, opcionalmente numa porta de atendimento")
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
        CustomerResponse response = customerService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/customers/" + response.id())).body(response);
    }

    @GetMapping
    @Operation(summary = "Lista os clientes")
    public List<CustomerResponse> findAll() {
        return customerService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca um cliente")
    public CustomerResponse findById(@PathVariable UUID id) {
        return customerService.findById(id);
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Histórico de situação e de porta do cliente, do mais antigo ao mais recente")
    public List<CustomerHistoryResponse> findHistory(@PathVariable UUID id) {
        return customerService.findHistory(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza o cliente, sua situação (ACTIVE, BLOCKED, CANCELLED) e a porta de atendimento")
    public CustomerResponse update(@PathVariable UUID id, @Valid @RequestBody CustomerRequest request) {
        return customerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Exclui um cadastro feito por engano (com o histórico); para encerrar contrato use CANCELLED")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        customerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
