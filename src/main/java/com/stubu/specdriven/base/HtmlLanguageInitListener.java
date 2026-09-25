package com.stubu.specdriven.base;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import org.springframework.stereotype.Component;

/**
 * Tells the browser and assistive technology the language of the page (the {@code lang} attribute of the html
 * element): the language the interface is shown in, which follows the language of the browser. Screen readers use it
 * to choose the pronunciation, and browsers to offer or suppress translation (WCAG 3.1.1).
 */
@Component
public class HtmlLanguageInitListener implements VaadinServiceInitListener {

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(initEvent -> {
            UI ui = initEvent.getUI();
            ui.getPage().executeJs("document.documentElement.lang = $0", ui.getLocale().getLanguage());
        });
    }
}
