package com.MSyamsandiYW.payment_service.outbox.impl;

import com.MSyamsandiYW.payment_service.outbox.Outbox;
import com.MSyamsandiYW.payment_service.outbox.OutboxRepository;
import com.MSyamsandiYW.payment_service.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
@Service
public class OutboxServiceImpl implements OutboxService {
    private final OutboxRepository outboxRepository;

    @Override
    public Mono<Void> save(Outbox outbox) {
        return outboxRepository.save(outbox).then();
    }
}
