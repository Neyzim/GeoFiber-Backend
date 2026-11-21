package com.geofiber.geofiber_backend.services.termination;

import com.geofiber.geofiber_backend.entities.box.BoxAssetEntity;
import com.geofiber.geofiber_backend.entities.cable.FiberEntity;
import com.geofiber.geofiber_backend.entities.connection.OpticalConnectionEntity;
import com.geofiber.geofiber_backend.entities.termination.FiberTerminationEntity;
import com.geofiber.geofiber_backend.enums.FiberEndEnum;
import com.geofiber.geofiber_backend.enums.FiberStatusEnum;
import com.geofiber.geofiber_backend.enums.OpticalConnectionTypeEnum;
import com.geofiber.geofiber_backend.enums.TerminationStatusEnum;
import com.geofiber.geofiber_backend.repositories.cable.FiberRepository;
import com.geofiber.geofiber_backend.repositories.connection.OpticalConnectionRepository;
import com.geofiber.geofiber_backend.repositories.termination.FiberTerminationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.function.Consumer;

import static com.geofiber.geofiber_backend.services.common.ServiceErrors.conflict;
import static com.geofiber.geofiber_backend.services.common.ServiceErrors.notFound;

/**
 * Regras das pontas de fibra (A e B), que é onde uma fibra se liga a portas de OLT,
 * portas de splitter e a outras fibras (fusões).
 *
 * <p>Convenção: a ponta A fica do lado da OLT (upstream) e a ponta B do lado do cliente.
 */
@Service
@Transactional
public class FiberTerminationService {
    private final FiberTerminationRepository terminationRepository;
    private final OpticalConnectionRepository connectionRepository;
    private final FiberRepository fiberRepository;

    public FiberTerminationService(
        FiberTerminationRepository terminationRepository,
        OpticalConnectionRepository connectionRepository,
        FiberRepository fiberRepository
    ) {
        this.terminationRepository = terminationRepository;
        this.connectionRepository = connectionRepository;
        this.fiberRepository = fiberRepository;
    }

    /** Toda fibra precisa de exatamente duas pontas; o banco confere isso no commit. */
    public void createEnds(FiberEntity fiber) {
        for (FiberEndEnum end : FiberEndEnum.values()) {
            FiberTerminationEntity termination = new FiberTerminationEntity();
            termination.setFiber(fiber);
            termination.setFiberEnd(end);
            terminationRepository.save(termination);
        }
    }

    /**
     * Troca a ponta de fibra ligada a uma porta. {@code fiberId} nulo apenas desliga a atual.
     *
     * @param current ponta ligada hoje à porta, ou nulo
     * @param attach  grava a porta na nova ponta
     * @return a ponta ligada após a operação, ou nulo
     */
    public FiberTerminationEntity relink(
        FiberTerminationEntity current, UUID fiberId, FiberEndEnum end,
        Consumer<FiberTerminationEntity> attach
    ) {
        if (current != null && current.getFiber().getId().equals(fiberId) && current.getFiberEnd() == end) {
            return current;
        }
        if (current != null) {
            release(current);
            // As colunas de porta são únicas: a ponta antiga precisa ser gravada antes da nova.
            terminationRepository.flush();
        }
        if (fiberId == null) {
            return null;
        }
        FiberTerminationEntity next = getAvailable(fiberId, end);
        attach.accept(next);
        markConnected(next);
        return next;
    }

    public FiberTerminationEntity getAvailable(UUID fiberId, FiberEndEnum end) {
        FiberEntity fiber = fiberRepository.findById(fiberId).orElseThrow(notFound("Fiber"));
        if (fiber.getStatus() == FiberStatusEnum.BROKEN || fiber.getStatus() == FiberStatusEnum.RESERVED) {
            throw conflict("Fiber is " + fiber.getStatus() + " and cannot be connected");
        }
        FiberTerminationEntity termination = terminationRepository.findByFiber_IdAndFiberEnd(fiberId, end)
            .orElseThrow(notFound("Fiber end " + end));
        boolean busy = termination.getStatus() != TerminationStatusEnum.AVAILABLE
            || termination.getOltPort() != null
            || termination.getSplitterPort() != null
            || termination.getServicePort() != null
            || connectionRepository.existsByTerminationA_IdOrTerminationB_Id(termination.getId(), termination.getId());
        if (busy) {
            throw conflict("Fiber end " + end + " is not available");
        }
        return termination;
    }

    /** Liga duas pontas já validadas por {@link #getAvailable} (fusão ou conector). */
    public OpticalConnectionEntity connect(
        FiberTerminationEntity first, FiberTerminationEntity second,
        OpticalConnectionTypeEnum type, BoxAssetEntity box, Double attenuationDb
    ) {
        boolean firstIsLower = compareAsPostgres(first.getId(), second.getId()) < 0;
        OpticalConnectionEntity connection = new OpticalConnectionEntity();
        connection.setType(type);
        connection.setBox(box);
        connection.setAttenuationDb(attenuationDb);
        connection.setTerminationA(firstIsLower ? first : second);
        connection.setTerminationB(firstIsLower ? second : first);
        markConnected(first);
        markConnected(second);
        return connectionRepository.save(connection);
    }

    public void release(FiberTerminationEntity termination) {
        termination.setOltPort(null);
        termination.setSplitterPort(null);
        termination.setServicePort(null);
        if (termination.getStatus() == TerminationStatusEnum.CONNECTED) {
            termination.setStatus(TerminationStatusEnum.AVAILABLE);
        }
        FiberEntity fiber = termination.getFiber();
        if (fiber.getStatus() == FiberStatusEnum.OCCUPIED) {
            boolean stillConnected = terminationRepository.findAllByFiber_Id(fiber.getId()).stream()
                .anyMatch(end -> end.getStatus() == TerminationStatusEnum.CONNECTED);
            if (!stillConnected) {
                fiber.setStatus(FiberStatusEnum.AVAILABLE);
            }
        }
    }

    private void markConnected(FiberTerminationEntity termination) {
        termination.setStatus(TerminationStatusEnum.CONNECTED);
        if (termination.getFiber().getStatus() == FiberStatusEnum.AVAILABLE) {
            termination.getFiber().setStatus(FiberStatusEnum.OCCUPIED);
        }
    }

    /**
     * O PostgreSQL compara UUIDs byte a byte (sem sinal), enquanto {@link UUID#compareTo} compara
     * longs com sinal. A constraint ck_connection_termination_order segue a ordem do banco.
     */
    static int compareAsPostgres(UUID first, UUID second) {
        int high = Long.compareUnsigned(first.getMostSignificantBits(), second.getMostSignificantBits());
        return high != 0 ? high
            : Long.compareUnsigned(first.getLeastSignificantBits(), second.getLeastSignificantBits());
    }
}
