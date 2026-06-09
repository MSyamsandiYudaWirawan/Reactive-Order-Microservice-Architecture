package com.MSyamsandiYW.inventory_service.stock_reservation;

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
@Table("stock_reservation")
public class StockReservation {
    @Id
    private UUID id;
    @Column("product_id")
    private String productId;
    @Column("transaction_id")
    private String transactionId;
    @Column("correlation_id")
    private String correlationId;
    @Column("qty")
    private Integer qty;
    @Column("status")
    private String status;

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
