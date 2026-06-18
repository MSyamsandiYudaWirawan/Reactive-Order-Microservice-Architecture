package com.MSyamsandiYW.inventory_service.product.impl;

import com.MSyamsandiYW.common.exception.BusinessException;
import com.MSyamsandiYW.common.exception.ErrorCode;
import com.MSyamsandiYW.common.jwt.JwtService;
import com.MSyamsandiYW.inventory_service.product.Product;
import com.MSyamsandiYW.inventory_service.product.ProductRepository;
import com.MSyamsandiYW.inventory_service.product.ProductService;
import com.MSyamsandiYW.inventory_service.product.request.GetProductsRequest;
import com.MSyamsandiYW.inventory_service.product.response.GetProductResponse;
import com.MSyamsandiYW.inventory_service.stock_reservation.StockReservation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {
    private final ProductRepository productRepository;
    private final JwtService jwtService;

    @Override
    public Mono<List<Product>> reserveStock(List<StockReservation> reservationList) {

        return productRepository.findAllById(reservationList.stream()
                        .map(r -> UUID.fromString(r.getProductId())).toList()).collectList()
                .flatMap(products -> {

                    //create reservation map for easy access
                    Map<String, StockReservation> reservationMap = reservationList.stream()
                            .collect(Collectors.toMap(StockReservation::getProductId, r -> r));

                    for (Product p : products) {
                        StockReservation r = reservationMap.get(p.getId().toString());
                        if (r != null) {
                            // validate if there is stock
                            if (p.getAvailableQty() < r.getQty() || !p.getIsActive() || p.getIsDeleted()) {
                                return Mono.error(new BusinessException(ErrorCode.OUT_OF_STOCK));
                            }
                            p.setAvailableQty(p.getAvailableQty() - r.getQty());
                            p.setReservedQty(p.getReservedQty() + r.getQty());
                            p.setUpdatedBy("INVENTORY_SERVICE");
                            p.setLastModifiedDate(Instant.now());
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

                    //create reservation map for easy access
                    Map<String, StockReservation> reservationMap = reservationList.stream()
                            .collect(Collectors.toMap(StockReservation::getProductId, r -> r));

                    for (Product p : products) {
                        StockReservation r = reservationMap.get(p.getId().toString());
                        if (r != null) {
                            p.setAvailableQty(p.getAvailableQty() + r.getQty());
                            p.setReservedQty(p.getReservedQty() - r.getQty());
                            p.setUpdatedBy("INVENTORY_SERVICE");
                            p.setLastModifiedDate(Instant.now());
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

                    //create reservation map for easy access
                    Map<String, StockReservation> reservationMap = reservationList.stream().collect(Collectors.toMap(StockReservation::getProductId, r -> r));

                    for (Product p : products) {
                        StockReservation r = reservationMap.get(p.getId().toString());
                        if (r != null) {
                            p.setReservedQty(p.getReservedQty() - r.getQty());
                            p.setSoldQty(p.getSoldQty() + r.getQty());
                            p.setUpdatedBy("INVENTORY_SERVICE");
                            p.setLastModifiedDate(Instant.now());
                        }

                    }
                    return productRepository.saveAll(products).collectList();
                });
    }

    @Override
    public Mono<ResponseEntity<List<GetProductResponse>>> getProductByIds(String token, GetProductsRequest request) {
        return jwtService.extractClaims(token)
                .then(productRepository.findAllById(request.getProductIds().stream().map(UUID::fromString).toList()).collectList())
                .flatMap(products -> {
                    if (products.isEmpty()) {
                        return Mono.error(new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
                    }
                    return Mono.just(ResponseEntity.ok(
                            products.stream().map(p -> GetProductResponse.builder()
                                    .productId(p.getId().toString())
                                    .price(p.getPrice())
                                    .build()
                            ).toList())
                    );
                });
    }
}
