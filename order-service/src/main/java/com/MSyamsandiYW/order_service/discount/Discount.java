package com.MSyamsandiYW.order_service.discount;

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
@Table("discounts")
public class Discount {

    @Id
    private UUID id;
    @Column("code")
    private String code;
    @Column("discount_type")
    private String discountType;
    @Column("value")
    private Double value;
    @Column("max_usage")
    private Integer maxUsage;
    @Column("valid_from")
    private ZonedDateTime validFrom;
    @Column("valid_until")
    private ZonedDateTime validUntil;
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
