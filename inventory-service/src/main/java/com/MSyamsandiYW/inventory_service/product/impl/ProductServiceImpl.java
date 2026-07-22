package com.MSyamsandiYW.inventory_service.product.impl;

import com.MSyamsandiYW.common.exception.BusinessException;
import com.MSyamsandiYW.common.exception.ErrorCode;
import com.MSyamsandiYW.common.jwt.JwtService;
import com.MSyamsandiYW.inventory_service.product.ProductRepository;
import com.MSyamsandiYW.inventory_service.product.ProductService;
import com.MSyamsandiYW.inventory_service.product.request.GetProductsRequest;
import com.MSyamsandiYW.inventory_service.product.response.GetProductResponse;
import com.MSyamsandiYW.inventory_service.stock_reservation.StockReservation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {
    private final ProductRepository productRepository;
    private final JwtService jwtService;
    private final TransactionalOperator transactionalOperator;

    @Override
    public Mono<Void> reserveStock(List<StockReservation> reservationList) {

        return Flux.fromIterable(reservationList)
                .flatMap(r -> productRepository.reserveStock(UUID.fromString(r.getProductId()), r.getQty())
                        .flatMap(rowsUpdated -> {
                            if (rowsUpdated == 0) {
                                log.warn("Insufficient stock for product ID: {}", r.getProductId());
                                return Mono.error(new BusinessException(ErrorCode.OUT_OF_STOCK));
                            }
                            return Mono.empty();
                        })
                ).then();
    }

    @Override
    public Mono<Void> releaseStock(List<StockReservation> reservationList) {
        return Flux.fromIterable(reservationList)
                .flatMap(r -> productRepository.releaseStock(UUID.fromString(r.getProductId()), r.getQty())
                        .flatMap(rowsUpdated -> {
                            if (rowsUpdated == 0) {
                                log.warn("Error release stock for product ID: {}", r.getProductId());
                                return Mono.error(new BusinessException(ErrorCode.INTERNAL_EXCEPTION));
                            }
                            return Mono.empty();
                        })
                )
                .as(transactionalOperator::transactional)
                .then();
    }

    @Override
    public Mono<Void> deductStock(List<StockReservation> reservationList) {
        return Flux.fromIterable(reservationList)
                .flatMap(r -> productRepository.deductStock(UUID.fromString(r.getProductId()), r.getQty())
                        .flatMap(rowsUpdated -> {
                            if (rowsUpdated == 0) {
                                log.warn("Error deduct stock for product ID: {}", r.getProductId());
                                return Mono.error(new BusinessException(ErrorCode.INTERNAL_EXCEPTION));
                            }
                            return Mono.empty();
                        })
                )
                .as(transactionalOperator::transactional)
                .then();
    }

    @Override
    public Mono<ResponseEntity<List<GetProductResponse>>> getProductByIds(String token, GetProductsRequest request, String correlationId) {
        log.info("Get product by ids: {} with correlationId: {}", request.getProductIds(), correlationId);
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
