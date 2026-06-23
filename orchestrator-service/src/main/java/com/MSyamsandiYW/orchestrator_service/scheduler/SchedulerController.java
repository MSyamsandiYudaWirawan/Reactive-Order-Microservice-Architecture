package com.MSyamsandiYW.orchestrator_service.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Slf4j
@RequiredArgsConstructor
public class SchedulerController {
    private final SchedulerService schedulerService;

    // runs every 30 minutes to expire stale saga transactions
    //TODO 2 minute for testing
    @Scheduled(cron = "0 */2 * * * *")
    public void runScheduler() {
        log.info("Payment expiry scheduler started");
        schedulerService.executeScheduler()
                .doOnSuccess(__ -> log.info("Payment expiry scheduler completed"))
                .doOnError(e -> log.error("Scheduler execution failed", e))
                .subscribe();
    }
}
