package com.stubu.specdriven.holiday;

import com.stubu.specdriven.audit.AuditAction;
import com.stubu.specdriven.audit.AuditJson;
import com.stubu.specdriven.audit.AuditService;
import com.stubu.specdriven.employee.AdminAccess;
import com.stubu.specdriven.holiday.HolidayValidationException.Problem;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Adds, changes and deletes public holidays (UC-012). Only administrators may do this; every operation acts for the
 * administrator it is given. A change and its audit entry are stored in one transaction. Holidays are for display
 * only: they never change worked time.
 */
@Service
public class PublicHolidayService {

    static final String ENTITY_TYPE = "PublicHoliday";
    private static final int MAX_NAME_LENGTH = 255;
    /** Dates outside this range are almost certainly typing mistakes. */
    private static final LocalDate EARLIEST = LocalDate.of(2000, 1, 1);
    private static final LocalDate LATEST = LocalDate.of(2100, 12, 31);

    private final PublicHolidayRepository holidays;
    private final AuditService auditService;
    private final AdminAccess adminAccess;
    private final TransactionTemplate transaction;

    public PublicHolidayService(PublicHolidayRepository holidays, AuditService auditService, AdminAccess adminAccess,
            PlatformTransactionManager transactionManager) {
        this.holidays = holidays;
        this.auditService = auditService;
        this.adminAccess = adminAccess;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** All holidays, earliest date first. */
    @Transactional(readOnly = true)
    public List<PublicHoliday> list(long adminId) {
        adminAccess.require(adminId);
        return holidays.findAllByOrderByDateAsc();
    }

    /**
     * Adds a holiday.
     *
     * @throws com.stubu.specdriven.employee.AdminOnlyException if the acting user is not an active administrator
     * @throws HolidayValidationException                       if the date or the name is not acceptable, or the
     *                                                          date already has a holiday
     */
    public PublicHoliday add(long adminId, LocalDate date, String name) {
        adminAccess.require(adminId);
        Set<Problem> problems = validate(date, name, null);
        if (!problems.isEmpty()) {
            throw new HolidayValidationException(problems, date);
        }
        try {
            return Objects.requireNonNull(transaction.execute(status -> {
                PublicHoliday saved = holidays.saveAndFlush(new PublicHoliday(date, name.strip()));
                auditService.record(adminId, ENTITY_TYPE, saved.getId(), AuditAction.CREATE, null, values(saved), null);
                return saved;
            }));
        } catch (DataIntegrityViolationException e) {
            // Somebody added the same date at the same moment (the unique constraint decides).
            throw new HolidayValidationException(EnumSet.of(Problem.DATE_TAKEN), date);
        }
    }

    /**
     * Changes the date and name of a holiday. Nothing is written when nothing changed.
     *
     * @throws HolidayNotFoundException  if there is no such holiday
     * @throws HolidayValidationException if the data is not acceptable
     */
    public PublicHoliday update(long adminId, long holidayId, LocalDate date, String name) {
        adminAccess.require(adminId);
        holidays.findById(holidayId).orElseThrow(HolidayNotFoundException::new);
        Set<Problem> problems = validate(date, name, holidayId);
        if (!problems.isEmpty()) {
            throw new HolidayValidationException(problems, date);
        }
        try {
            return Objects.requireNonNull(transaction.execute(status -> {
                PublicHoliday holiday = holidays.findById(holidayId).orElseThrow(HolidayNotFoundException::new);
                String before = values(holiday);
                holiday.setDate(date);
                holiday.setName(name.strip());
                String after = values(holiday);
                if (!before.equals(after)) {
                    holidays.saveAndFlush(holiday);
                    auditService.record(adminId, ENTITY_TYPE, holidayId, AuditAction.UPDATE, before, after, null);
                }
                return holiday;
            }));
        } catch (DataIntegrityViolationException e) {
            throw new HolidayValidationException(EnumSet.of(Problem.DATE_TAKEN), date);
        }
    }

    /**
     * Deletes a holiday for good; the audit entry keeps what it was.
     *
     * @throws HolidayNotFoundException if there is no such holiday
     */
    public void delete(long adminId, long holidayId) {
        adminAccess.require(adminId);
        holidays.findById(holidayId).orElseThrow(HolidayNotFoundException::new);
        transaction.executeWithoutResult(status -> {
            PublicHoliday holiday = holidays.findById(holidayId).orElseThrow(HolidayNotFoundException::new);
            String before = values(holiday);
            holidays.delete(holiday);
            holidays.flush();
            auditService.record(adminId, ENTITY_TYPE, holidayId, AuditAction.DELETE, before, null, null);
        });
    }

    /** {@code ownId} is the holiday being edited, whose own date does not count as taken. */
    private Set<Problem> validate(LocalDate date, String name, Long ownId) {
        Set<Problem> problems = EnumSet.noneOf(Problem.class);
        if (date == null || date.isBefore(EARLIEST) || date.isAfter(LATEST)) {
            problems.add(Problem.DATE_INVALID);
        } else {
            Optional<PublicHoliday> sameDate = holidays.findByDate(date);
            if (sameDate.isPresent() && !sameDate.get().getId().equals(ownId)) {
                problems.add(Problem.DATE_TAKEN);
            }
        }
        if (name == null || name.isBlank()) {
            problems.add(Problem.NAME_REQUIRED);
        } else if (name.strip().length() > MAX_NAME_LENGTH) {
            problems.add(Problem.NAME_TOO_LONG);
        }
        return problems;
    }

    private static String values(PublicHoliday holiday) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("date", holiday.getDate().toString());
        values.put("name", holiday.getName());
        return AuditJson.object(values);
    }
}
