package com.MSyamsandiYW.inventory_service.product.impl;

import com.MSyamsandiYW.common.exception.BusinessException;
import com.MSyamsandiYW.common.exception.ErrorCode;
import com.MSyamsandiYW.inventory_service.product.Product;
import com.MSyamsandiYW.inventory_service.product.ProductRepository;
import com.MSyamsandiYW.inventory_service.product.ProductService;
import com.MSyamsandiYW.inventory_service.stock_reservation.StockReservation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {
    private final ProductRepository productRepository;

    @Override
    public Mono<List<Product>> reserveStock(List<StockReservation> reservationList) {

        return productRepository.findAllById(reservationList.stream()
                        .map(r -> UUID.fromString(r.getProductId())).toList()).collectList()
                .flatMap(products -> {
                    for (Product p : products) {
                        for (StockReservation r : reservationList) {
                            if (p.getId().toString().equals(r.getProductId())) {
                                p.setAvailableQty(p.getAvailableQty() - r.getQty());
                                p.setReservedQty(p.getReservedQty() + r.getQty());
                                p.setUpdatedBy("INVENTORY_SERVICE");
                                p.setLastModifiedDate(ZonedDateTime.now());
                            }
                        }
                    }
                    return productRepository.saveAll(products).collectList();
                });
    }

    @Override
    public Mono<List<Product>> releaseStock(List<StockReservation> reservationList) {
        return productRepository.findAllById(reservationList.stream()
                        .map(r -> UUID.fromString(r.getProductId())).toList()).collectList()
                .flatMap(products -> {
                    for (Product p : products) {
                        for (StockReservation r : reservationList) {
                            if (p.getId().toString().equals(r.getProductId())) {
                                // validate if there is stock
                                if(p.getAvailableQty() < r.getQty()){
                                    return Mono.error(new BusinessException(ErrorCode.OUT_OF_STOCK));
                                }
                                p.setAvailableQty(p.getAvailableQty() + r.getQty());
                                p.setReservedQty(p.getReservedQty() - r.getQty());
                                p.setUpdatedBy("INVENTORY_SERVICE");
                                p.setLastModifiedDate(ZonedDateTime.now());
                            }
                        }
                    }
                    // save batch if ALL product is valid means we have stock
                    return productRepository.saveAll(products).collectList();
                });
    }

    @Override
    public Mono<List<Product>> deductStock(List<StockReservation> reservationList) {
        return productRepository.findAllById(reservationList.stream()
                        .map(r -> UUID.fromString(r.getProductId())).toList()).collectList()
                .flatMap(products -> {
                    for (Product p : products) {
                        for (StockReservation r : reservationList) {
                            if (p.getId().toString().equals(r.getProductId())) {
                                p.setReservedQty(p.getReservedQty() - r.getQty());
                                p.setSoldQty(p.getSoldQty() + r.getQty());
                                p.setUpdatedBy("INVENTORY_SERVICE");
                                p.setLastModifiedDate(ZonedDateTime.now());
                            }
                        }
                    }
                    return productRepository.saveAll(products).collectList();
                });
    }
}
