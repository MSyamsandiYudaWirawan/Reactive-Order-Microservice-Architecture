package com.MSyamsandiYW.orchestrator_service.kafka.event;

import lombok.*;

import java.time.Instant;
import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class DlqEventPayload {
    private String originalTopic;
    private String originalKey;
    private Object originalPayload;
    private String errorMessage;
    private Instant timestamp;
}
