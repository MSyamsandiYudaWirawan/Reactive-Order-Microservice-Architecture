package com.MSyamsandiYW.inventory_service.product;

import com.MSyamsandiYW.inventory_service.product.response.GetProductResponse;
import com.MSyamsandiYW.inventory_service.product.request.GetProductsRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.springframework.http.HttpStatus.OK;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
@Tag(name = "Product", description = "Product API")
public class ProductController {
    private final ProductService productService;

    @PostMapping("/list")
    @ResponseStatus(code = OK)
    public Mono<ResponseEntity<List<GetProductResponse>>> getProductByIds(
            @RequestBody @Valid GetProductsRequest request,
            @RequestHeader("Authorization") String token
    ) {
        return productService.getProductByIds(token,request);
    }
}
