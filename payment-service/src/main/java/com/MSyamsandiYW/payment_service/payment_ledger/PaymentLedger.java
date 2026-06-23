package com.MSyamsandiYW.payment_service.payment_ledger;

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
@Table(name = "payment_ledger")
public class PaymentLedger {

    @Id
    private UUID id;
    @Column("payment_id")
    private String paymentId;
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
