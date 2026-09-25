package com.stubu.specdriven;

import com.vaadin.flow.theme.aura.Aura;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.AppShellSettings;

@SpringBootApplication
@StyleSheet(Aura.STYLESHEET)
@StyleSheet("styles.css") // Your custom styles
@Push
public class Application implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    /** The icon mark in the browser tab, and the colour of the browser toolbar on phones. */
    @Override
    public void configurePage(AppShellSettings settings) {
        settings.addFavIcon("icon", com.stubu.specdriven.base.AppLogo.PATH, "any");
        settings.addMetaTag("theme-color", "#0e7a4b");
    }

}
