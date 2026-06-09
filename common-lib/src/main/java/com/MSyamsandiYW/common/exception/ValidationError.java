package com.MSyamsandiYW.common.exception;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class ValidationError {
    private String field;
    private String code;
    private String message;
}