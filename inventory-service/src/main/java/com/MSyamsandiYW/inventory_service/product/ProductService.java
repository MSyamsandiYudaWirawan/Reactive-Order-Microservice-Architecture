package com.MSyamsandiYW.inventory_service.product;

import com.MSyamsandiYW.inventory_service.product.response.GetProductResponse;
import com.MSyamsandiYW.inventory_service.product.request.GetProductsRequest;
import com.MSyamsandiYW.inventory_service.stock_reservation.StockReservation;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ProductService {
    Mono<Void> reserveStock(List<StockReservation> reservationList);

    Mono<Void> releaseStock(List<StockReservation> reservationList);

    Mono<Void> deductStock(List<StockReservation> reservationList);

    Mono<ResponseEntity<List<GetProductResponse>>> getProductByIds(String token, GetProductsRequest request, String correlationId);
}
