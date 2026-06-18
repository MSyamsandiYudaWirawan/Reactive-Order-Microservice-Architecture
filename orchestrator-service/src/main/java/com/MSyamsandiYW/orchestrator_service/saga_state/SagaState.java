package com.MSyamsandiYW.orchestrator_service.saga_state;

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
@Table(name = "saga_state")
public class SagaState {

    @Id
    private UUID id;
    @Column("transaction_id")
    private String transactionId;
    @Column("correlation_id")
    private String correlationId;
    @Column("stock_status")
    private String stockStatus;
    @Column("payment_status")
    private String paymentStatus;
    @Column("saga_status")
    private String sagaStatus;

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
