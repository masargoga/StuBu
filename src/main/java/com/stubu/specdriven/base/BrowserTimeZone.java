package com.stubu.specdriven.base;

import com.vaadin.flow.component.UI;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds out the time zone of the user's browser. It decides what "today" is and how times are shown, while the server
 * time zone is only the starting point until the browser has answered.
 */
public final class BrowserTimeZone {

    private static final Logger log = LoggerFactory.getLogger(BrowserTimeZone.class);

    private BrowserTimeZone() {
    }

    /**
     * Asks the browser for its time zone and tells {@code changed} when it differs from {@code current}. A zone the
     * server does not know is ignored, the page keeps the zone it has.
     *
     * @param current the zone the page uses now; {@code null} always reports the browser's zone
     */
    public static void detect(UI ui, ZoneId current, Consumer<ZoneId> changed) {
        ui.getPage().retrieveExtendedClientDetails(details -> {
            try {
                ZoneId browserZone = ZoneId.of(details.getTimeZoneId());
                if (!browserZone.equals(current)) {
                    changed.accept(browserZone);
                }
            } catch (DateTimeException | NullPointerException unknownZone) {
                log.debug("Keeping the server time zone, the browser reported {}", details.getTimeZoneId());
            }
        });
    }
}
