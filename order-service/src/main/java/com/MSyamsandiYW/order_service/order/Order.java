package com.MSyamsandiYW.order_service.order;

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
@Table("orders")
public class Order {

    @Id
    private UUID id;
    @Column("correlation_id")
    private String correlationId;
    @Column("transaction_id")
    private String transactionId;
    @Column("user_id")
    private String userId;
    @Column("payment_id")
    private String paymentId;
    @Column("order_status")
    private String orderStatus;
    @Column("total_amount")
    private Double totalAmount;
    @Column("payment_method")
    private String paymentMethod;
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
