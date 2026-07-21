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
@Table("order_status_history")
public class OrderStatusHistory {

    @Id
    private UUID id;
    @Column("transaction_id")
    private String transactionId;
    @Column("correlation_id")
    private String correlationId;
    @Column("status")
    private String status;

    @CreatedDate
    @Column("created_at")
    private Instant createdAt;
}
