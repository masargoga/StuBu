package com.stubu.specdriven.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

/** The application starts with the profile of the Docker image and logs structured JSON (spec.md section 48). */
@SpringBootTest(properties = { "vaadin.launch-browser=false", "vaadin.devmode.devTools.enabled=false" })
@ActiveProfiles({ "test", "prod" })
class ProductionProfileTest {

    @Autowired
    Environment environment;

    @Test
    void theApplicationStartsWithTheProductionProfileAndLogsStructured() {
        assertEquals("ecs", environment.getProperty("logging.structured.format.console"));
    }
}
