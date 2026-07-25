package com.MSyamsandiYW.order_service.outbox;

import io.r2dbc.postgresql.codec.Json;
import lombok.*;
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
@Table("outbox")
public class Outbox {
    @Id
    private UUID id;
    @Column("aggregate_type")
    private String aggregateType;
    @Column("aggregate_id")
    private String aggregateId;
    @Column("event_type")
    private String eventType;
    @Column("payload")
    private Json payload;
    @Column("created_at")
    private Instant createdAt;
}
