package com.stubu.specdriven.notification;

import com.stubu.specdriven.base.TranslationProvider;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends the notifications by email. A mail server is configured with the usual {@code spring.mail.*}
 * properties; without one (local development, tests) the message is only logged. The texts come from the
 * translation files, in the language set by {@code stubu.notifications.locale}.
 */
@Service
public class EmailNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final TranslationProvider translations;
    private final String from;
    private final Locale locale;
    private final ZoneId zone;

    public EmailNotificationService(ObjectProvider<JavaMailSender> mailSender, TranslationProvider translations,
            @Value("${stubu.notifications.from:noreply@stubu.local}") String from,
            @Value("${stubu.notifications.locale:en}") String locale,
            @Value("${stubu.notifications.zone:UTC}") String zone) {
        this.mailSender = mailSender;
        this.translations = translations;
        this.from = from;
        this.locale = Locale.forLanguageTag(locale);
        this.zone = ZoneId.of(zone);
    }

    @Override
    public void timesheetSubmitted(TimesheetSubmittedNotice notice) {
        String month = notice.period().getMonth().getDisplayName(TextStyle.FULL_STANDALONE, locale) + " "
                + notice.period().getYear();
        String submittedOn = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale)
                .withZone(zone).format(notice.submittedAt());
        String subject = translations.getTranslation("mail.submitted.subject", locale, notice.employeeName(), month);
        String body = translations.getTranslation("mail.submitted.body", locale, notice.recipientName(),
                notice.employeeName(), month, submittedOn);

        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.info("No mail server configured, not sending: \"{}\" to {}", subject, notice.recipientEmail());
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(notice.recipientEmail());
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
    }
}
