package com.MSyamsandiYW.inventory_service.product;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@Table("products")
public class Product {

    @Id
    private UUID id;
    @Column("name")
    private String name;
    @Column("price")
    private Double price;
    @Column("available_qty")
    private Integer availableQty;
    @Column("reserved_qty")
    private Integer reservedQty;
    @Column("sold_qty")
    private Integer soldQty;
    @Column("description")
    private String description;
    @Column("is_active")
    private Boolean isActive;
    @Column("is_deleted")
    private Boolean isDeleted;

    @Column("created_by")
    private String createdBy;
    @Column("updated_by")
    private String updatedBy;
    @CreatedDate
    @Column("created_date")
    private Instant createdDate;
    @LastModifiedDate
    @Column("last_modified_date")
    private Instant lastModifiedDate;

}
