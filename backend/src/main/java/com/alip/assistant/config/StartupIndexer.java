package com.alip.assistant.config;

import com.alip.assistant.service.FileIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartupIndexer {

    private final FileIndexService fileIndexService;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("Application started. Triggering initial file indexing...");
        fileIndexService.reindex();
    }
}
