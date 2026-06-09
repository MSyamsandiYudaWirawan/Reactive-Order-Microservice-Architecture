package com.MSyamsandiYW.common.exception;

import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class ErrorResponse {
    private String message;
    private String code;
    private List<ValidationError> validationErrors;
}