package com.MSyamsandiYW.payment_service.payment;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;
    @Column("user_id")
    private String userId;
    @Column("transaction_id")
    private String transactionId;
    @Column("correlation_id")
    private String correlationId;
    @Column("payment_method")
    private String paymentMethod;
    @Column("amount")
    private BigDecimal amount;
    @Column("status")
    private String status;
    @Column("failure_code")
    private String failureCode;
    @Column("failure_message")
    private String failureMessage;
    @Column("created_by")
    private String createdBy;
    @Column("updated_by")
    private String updatedBy;

    @CreatedDate
    @Column("created_at")
    private Instant createdAt;
    @LastModifiedDate
    @Column("updated_at")
    private Instant updatedAt;
}
