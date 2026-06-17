package com.MSyamsandiYW.order_service.order_item;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.ZonedDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@Table("order_items")
public class OrderItem {

    @Id
    private UUID id;
    @Column("correlation_id")
    private String correlationId;
    @Column("transaction_id")
    private String transactionId;
    @Column("product_id")
    private String productId;
    @Column("quantity")
    private Integer quantity;
    @Column("price")
    private Double price;
    @Column("created_by")
    private String createdBy;
    @Column("updated_by")
    private String updatedBy;

    @CreatedDate
    @Column("created_date")
    private ZonedDateTime createdDate;
    @LastModifiedDate
    @Column("last_modified_date")
    private ZonedDateTime lastModifiedDate;
}
