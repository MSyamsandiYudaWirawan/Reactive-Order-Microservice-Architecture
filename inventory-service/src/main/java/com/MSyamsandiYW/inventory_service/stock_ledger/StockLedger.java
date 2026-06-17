package com.MSyamsandiYW.inventory_service.stock_ledger;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.ZonedDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@Table("stock_ledger")
public class StockLedger {

    @Id
    private UUID id;
    @Column("product_id")
    private String productId;
    @Column("transaction_id")
    private String transactionId;
    @Column("correlation_id")
    private String correlationId;
    @Column("event_type")
    private String eventType;
    @Column("qty")
    private Integer qty;

    @CreatedDate
    @Column("created_date")
    private ZonedDateTime createdDate;
}
