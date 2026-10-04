package com.ali6eza.shortlink.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class StartupLogger {
    private static final Logger log = LoggerFactory.getLogger(StartupLogger.class);

    @EventListener(ApplicationReadyEvent.class)
    public void logSwaggerUrl(ApplicationReadyEvent event) {
        Environment environment = event.getApplicationContext().getEnvironment();

        String port = environment.getProperty("local.server.port");
        if (port == null) {
            return;
        }

        String contextPath = environment.getProperty("server.servlet.context-path", "");

        log.info("Swagger UI: http://localhost:{}{}/swagger-ui/index.html", port, contextPath);
    }
}
