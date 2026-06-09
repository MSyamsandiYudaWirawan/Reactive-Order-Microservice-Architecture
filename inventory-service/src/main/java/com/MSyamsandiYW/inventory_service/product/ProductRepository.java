package com.MSyamsandiYW.inventory_service.product;

import org.springframework.data.r2dbc.repository.R2dbcRepository;

import java.util.UUID;

public interface ProductRepository extends R2dbcRepository<Product, UUID> {
}
