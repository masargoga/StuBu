package com.stubu.specdriven.region;

import com.stubu.specdriven.audit.AuditAction;
import com.stubu.specdriven.audit.AuditJson;
import com.stubu.specdriven.audit.AuditService;
import com.stubu.specdriven.employee.AdminAccess;
import com.stubu.specdriven.employee.EditConflictException;
import com.stubu.specdriven.employee.EmployeeRepository;
import com.stubu.specdriven.holiday.PublicHolidayRepository;
import com.stubu.specdriven.region.RegionInUseException.Reason;
import com.stubu.specdriven.region.RegionValidationException.Problem;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Adds, renames and deletes regions (UC-017). Only administrators may do this; every operation acts for the
 * administrator it is given. A change and its audit entry are stored in one transaction. A region that employees or
 * holidays still use, and the last region, cannot be deleted.
 */
@Service
public class RegionService {

    static final String ENTITY_TYPE = "Region";
    private static final int MAX_NAME_LENGTH = 100;

    private final RegionRepository regions;
    private final EmployeeRepository employees;
    private final PublicHolidayRepository holidays;
    private final AuditService auditService;
    private final AdminAccess adminAccess;
    private final TransactionTemplate transaction;

    public RegionService(RegionRepository regions, EmployeeRepository employees, PublicHolidayRepository holidays,
            AuditService auditService, AdminAccess adminAccess, PlatformTransactionManager transactionManager) {
        this.regions = regions;
        this.employees = employees;
        this.holidays = holidays;
        this.auditService = auditService;
        this.adminAccess = adminAccess;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** All regions by name, with how many employees and holidays each has. */
    @Transactional(readOnly = true)
    public List<RegionRow> list(long adminId) {
        adminAccess.require(adminId);
        Map<Long, Long> employeeCounts = counts(employees.countByRegion());
        Map<Long, Long> holidayCounts = counts(holidays.countByRegion());
        return regions.findAllByOrderByNameAsc().stream().map(region -> new RegionRow(region.getId(), region.getName(),
                employeeCounts.getOrDefault(region.getId(), 0L), holidayCounts.getOrDefault(region.getId(), 0L),
                region.getVersion() == null ? 0 : region.getVersion())).toList();
    }

    private static Map<Long, Long> counts(List<Object[]> rows) {
        Map<Long, Long> counts = new HashMap<>();
        rows.forEach(row -> counts.put((Long) row[0], (Long) row[1]));
        return counts;
    }

    /**
     * Adds a region.
     *
     * @throws com.stubu.specdriven.employee.AdminOnlyException if the acting user is not an active administrator
     * @throws RegionValidationException                        if the name is empty, too long or already used
     */
    public Region add(long adminId, String name) {
        adminAccess.require(adminId);
        Set<Problem> problems = validate(name, null);
        if (!problems.isEmpty()) {
            throw new RegionValidationException(problems);
        }
        try {
            return Objects.requireNonNull(transaction.execute(status -> {
                Region saved = regions.saveAndFlush(new Region(name.strip()));
                auditService.record(adminId, ENTITY_TYPE, saved.getId(), AuditAction.CREATE, null, values(saved), null);
                return saved;
            }));
        } catch (DataIntegrityViolationException e) {
            // Somebody added the same name at the same moment (the unique constraint decides).
            throw new RegionValidationException(EnumSet.of(Problem.NAME_TAKEN));
        }
    }

    /**
     * Renames a region that the administrator saw at the given version. Nothing is written when the name is the same.
     *
     * @param expectedVersion the version the dialog was opened with; {@code null} skips the check
     * @throws RegionNotFoundException if there is no such region
     * @throws EditConflictException   if somebody else changed the region since
     */
    public Region rename(long adminId, long regionId, String name, Long expectedVersion) {
        adminAccess.require(adminId);
        checkVersion(regions.findById(regionId).orElseThrow(RegionNotFoundException::new), expectedVersion);
        Set<Problem> problems = validate(name, regionId);
        if (!problems.isEmpty()) {
            throw new RegionValidationException(problems);
        }
        try {
            return Objects.requireNonNull(transaction.execute(status -> {
                Region region = regions.findById(regionId).orElseThrow(RegionNotFoundException::new);
                checkVersion(region, expectedVersion);
                String before = values(region);
                region.setName(name.strip());
                String after = values(region);
                if (!before.equals(after)) {
                    regions.saveAndFlush(region);
                    auditService.record(adminId, ENTITY_TYPE, regionId, AuditAction.UPDATE, before, after, null);
                }
                return region;
            }));
        } catch (DataIntegrityViolationException e) {
            throw new RegionValidationException(EnumSet.of(Problem.NAME_TAKEN));
        } catch (ObjectOptimisticLockingFailureException concurrent) {
            throw new EditConflictException();
        }
    }

    /**
     * Deletes a region for good; the audit entry keeps what it was.
     *
     * @throws RegionNotFoundException if there is no such region
     * @throws RegionInUseException    if employees or holidays use it, or it is the last region
     */
    public void delete(long adminId, long regionId) {
        adminAccess.require(adminId);
        regions.findById(regionId).orElseThrow(RegionNotFoundException::new);
        transaction.executeWithoutResult(status -> {
            Region region = regions.findById(regionId).orElseThrow(RegionNotFoundException::new);
            if (regions.count() <= 1) {
                throw new RegionInUseException(Reason.LAST_REGION, 0);
            }
            long assigned = employees.countByRegionId(regionId);
            if (assigned > 0) {
                throw new RegionInUseException(Reason.EMPLOYEES, assigned);
            }
            long withHolidays = holidays.countByRegionId(regionId);
            if (withHolidays > 0) {
                throw new RegionInUseException(Reason.HOLIDAYS, withHolidays);
            }
            String before = values(region);
            regions.delete(region);
            regions.flush();
            auditService.record(adminId, ENTITY_TYPE, regionId, AuditAction.DELETE, before, null, null);
        });
    }

    private static void checkVersion(Region region, Long expectedVersion) {
        if (expectedVersion != null && !expectedVersion.equals(region.getVersion())) {
            throw new EditConflictException();
        }
    }

    /** {@code ownId} is the region being renamed, whose own name does not count as taken. */
    private Set<Problem> validate(String name, Long ownId) {
        Set<Problem> problems = EnumSet.noneOf(Problem.class);
        if (name == null || name.isBlank()) {
            problems.add(Problem.NAME_REQUIRED);
        } else if (name.strip().length() > MAX_NAME_LENGTH) {
            problems.add(Problem.NAME_TOO_LONG);
        } else {
            regions.findByNameIgnoreCase(name.strip()).filter(same -> !same.getId().equals(ownId))
                    .ifPresent(same -> problems.add(Problem.NAME_TAKEN));
        }
        return problems;
    }

    private static String values(Region region) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", region.getName());
        return AuditJson.object(values);
    }
}
