package com.stubu.specdriven.settings;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and stores the personal settings of an employee (UC-016). Every operation acts on the employee it is given,
 * which callers take from the signed-in user, never from user input. Changing a personal setting is not a business
 * change and is not written to the audit log.
 */
@Service
public class EmployeeSettingsService {

    private final EmployeeSettingRepository settings;

    public EmployeeSettingsService(EmployeeSettingRepository settings) {
        this.settings = settings;
    }

    /** The language the employee chose, or nothing if they never did (or the stored value is not a known language). */
    @Transactional(readOnly = true)
    public Optional<AppLanguage> languageOf(long employeeId) {
        return settings.findById(employeeId).map(EmployeeSetting::getLanguage).flatMap(AppLanguage::fromCode);
    }

    /** The language of the employee with this email address, for emails; nothing if they never chose one. */
    @Transactional(readOnly = true)
    public Optional<AppLanguage> languageOfEmail(String email) {
        return email == null ? Optional.empty() : settings.findLanguageByEmail(email).flatMap(AppLanguage::fromCode);
    }

    /**
     * Stores the language of the employee. Nothing is written when it is already stored.
     *
     * @return whether something was stored
     * @throws org.springframework.dao.DataAccessException if the database fails
     */
    @Transactional
    public boolean saveLanguage(long employeeId, AppLanguage language) {
        EmployeeSetting setting = settings.findById(employeeId).orElseGet(() -> new EmployeeSetting(employeeId));
        if (language.code().equals(setting.getLanguage())) {
            return false;
        }
        setting.setLanguage(language.code());
        settings.saveAndFlush(setting);
        return true;
    }
}
