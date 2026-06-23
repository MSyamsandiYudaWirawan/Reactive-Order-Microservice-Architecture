package com.MSyamsandiYW.order_service.order_ledger;


import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@Table("order_ledger")
public class OrderLedger {

    @Id
    private UUID id;
    @Column("transaction_id")
    private String transactionId;
    @Column("correlation_id")
    private String correlationId;
    @Column("event_type")
    private String eventType;

    @CreatedDate
    @Column("created_date")
    private Instant createdDate;
}
