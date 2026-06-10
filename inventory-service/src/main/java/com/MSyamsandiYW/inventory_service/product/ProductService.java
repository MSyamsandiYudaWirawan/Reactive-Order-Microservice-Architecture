package com.MSyamsandiYW.inventory_service.product;

import com.MSyamsandiYW.inventory_service.product.response.GetProductResponse;
import com.MSyamsandiYW.inventory_service.product.request.GetProductsRequest;
import com.MSyamsandiYW.inventory_service.stock_reservation.StockReservation;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ProductService {
    Mono<List<Product>> reserveStock(List<StockReservation> reservationList);

    Mono<List<Product>> releaseStock(List<StockReservation> reservationList);

    Mono<List<Product>> deductStock(List<StockReservation> reservationList);

    Mono<ResponseEntity<List<GetProductResponse>>> getProductByIds(String token, GetProductsRequest request);
}
