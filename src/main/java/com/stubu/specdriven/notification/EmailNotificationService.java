package com.stubu.specdriven.notification;

import com.stubu.specdriven.base.TranslationProvider;
import java.time.Instant;
import java.time.YearMonth;
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
        String month = monthLabel(notice.period());
        String key = notice.resubmission() ? "mail.resubmitted" : "mail.submitted";
        String subject = translations.getTranslation(key + ".subject", locale, notice.employeeName(), month);
        String text = translations.getTranslation(key + ".body", locale, notice.recipientName(),
                notice.employeeName(), month, dateTime(notice.submittedAt()));
        send(notice.recipientEmail(), subject, text);
    }

    @Override
    public void timesheetApproved(TimesheetDecisionNotice notice) {
        String month = monthLabel(notice.period());
        String subject = translations.getTranslation("mail.approved.subject", locale, month);
        String text = translations.getTranslation("mail.approved.body", locale, notice.recipientName(), month,
                notice.reviewerName(), dateTime(notice.decidedAt()));
        send(notice.recipientEmail(), subject, text);
    }

    @Override
    public void timesheetRejected(TimesheetDecisionNotice notice) {
        String month = monthLabel(notice.period());
        String subject = translations.getTranslation("mail.rejected.subject", locale, month);
        String text = translations.getTranslation("mail.rejected.body", locale, notice.recipientName(), month,
                notice.reviewerName(), dateTime(notice.decidedAt()), notice.reason());
        send(notice.recipientEmail(), subject, text);
    }

    private String monthLabel(YearMonth period) {
        return period.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, locale) + " " + period.getYear();
    }

    private String dateTime(Instant instant) {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale).withZone(zone)
                .format(instant);
    }

    private void send(String recipient, String subject, String text) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.info("No mail server configured, not sending: \"{}\" to {}", subject, recipient);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(text);
        sender.send(message);
    }
}
