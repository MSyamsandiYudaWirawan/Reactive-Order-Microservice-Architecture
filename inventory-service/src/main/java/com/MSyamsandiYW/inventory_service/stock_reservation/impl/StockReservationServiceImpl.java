package com.MSyamsandiYW.inventory_service.stock_reservation.impl;

import com.MSyamsandiYW.inventory_service.kafka.event.StockCommand;
import com.MSyamsandiYW.inventory_service.stock_reservation.StockReservation;
import com.MSyamsandiYW.inventory_service.stock_reservation.StockReservationRepository;
import com.MSyamsandiYW.inventory_service.stock_reservation.StockReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.ZonedDateTime;
import java.util.List;

import static com.MSyamsandiYW.inventory_service.properties.AppConstant.RESERVATION_STATUS.RESERVED;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockReservationServiceImpl implements StockReservationService {

    private final StockReservationRepository repository;

    @Override
    public Mono<List<StockReservation>> reserveStock(StockCommand command) {
        List<StockReservation> reservationList = command.getItems().stream()
                .map(item -> StockReservation.builder()
                        .productId(item.getProductId())
                        .transactionId(command.getTransactionId())
                        .correlationId(command.getCorrelationId())
                        .qty(item.getQuantity())
                        .status(RESERVED.name())
                        .createdBy("INVENTORY_SERVICE")
                        .updatedBy("INVENTORY_SERVICE")
                        .createdDate(ZonedDateTime.now())
                        .lastModifiedDate(ZonedDateTime.now())
                        .build()
                ).toList();

        return repository.saveAll(reservationList).collectList();
    }

    @Override
    public Mono<List<StockReservation>> updateStatusReservation(String transactionId, String statusReservation) {
        return repository.findAllByTransactionId(transactionId).collectList()
                .flatMap(reservationList -> {
                    List<StockReservation> updatedReservationList = reservationList.stream().peek(r -> {
                        r.setStatus(statusReservation);
                        r.setUpdatedBy("INVENTORY_SERVICE");
                        r.setLastModifiedDate(ZonedDateTime.now());
                    }).toList();
                    return repository.saveAll(updatedReservationList).collectList();
                });
    }
}
