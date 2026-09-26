# UC-016: Choose Application Language

---

**Goal:** As an employee (also manager and admin), I want to choose myself which language the application is shown in, so that I use the language that fits me best.

**Status:** Implemented
**Date:** 2026-09-26

> A use case cannot be marked as **Implemented** unless all criteria in the use-case implementation workflow are fulfilled.

> **Note:** The application is available in English, German, Spanish and French. The English and German texts already exist; the Spanish and French texts are new first drafts and should be proofread by native speakers before the application is used in production, especially the wording of business terms.

---

## Actors

- **Primary actor:** Any user of the application: employee, manager or administrator, signed in or still on the login page.

---

## Preconditions

- None to use the language selector; it is also available on the login page.
- To have the choice stored in the employee's settings, the user is signed in.

---

## Trigger

The user opens the language selector in the header of an application page, or in the corner of the login card.

---

## Main Flow

1. User opens the language selector.
2. System shows the four languages, each with a flag and its name written in that language: English (flag of the United Kingdom), Deutsch (Germany), Español (Spain) and Français (France). The current language is marked.
3. User chooses a language.
4. System switches the page immediately: all texts, dates, times and numbers, the page title, and the language of the page (the `lang` attribute). The user stays on the same page with the same data; nothing is reloaded from scratch and the user is not signed out.
5. System remembers the choice in the browser and, when the user is signed in, also in the employee's settings (see BR-04).
6. System shows the chosen language on every later page, every later visit and every device the employee signs in on.

**Which language is shown when a page opens:** the signed-in employee's stored language; otherwise the choice remembered in the browser; otherwise the browser's language if it is one of the four; otherwise English.

---

## Alternative Flows

### AF-1: Saving in the Employee's Settings Fails

**Branches from:** Main Flow step 5
**Condition:** The database operation fails

1. The page has already switched to the chosen language and the choice is kept in the browser.
2. System displays a message: "Your language could not be saved to your settings. It applies on this device only."
3. The employee's stored language is unchanged. The user can choose again.
4. Use case ends.

### AF-2: Choice Made on the Login Page

**Branches from:** Main Flow step 5
**Condition:** Nobody is signed in yet

1. System keeps the choice in the browser only.
2. When the user signs in, the language stored in their settings wins and is shown. If their settings have no language yet, the language chosen in the browser is stored in their settings.
3. Use case ends.

### AF-3: Unsupported Language

**Branches from:** Start of any page
**Condition:** A remembered choice or the browser language is not one of the four languages (for example Italian, or a changed cookie)

1. System ignores it and applies the next rule of the order above, at last English.
2. Use case ends.

### AF-4: Same Language Chosen Again

**Branches from:** Main Flow step 3
**Condition:** User chooses the language that is already shown

1. Nothing changes and nothing is stored.
2. Use case ends.

---

## Postconditions

- **On success:** The application is shown in the chosen language; the choice is remembered in the browser and, when signed in, in the employee's settings; emails to this employee use it from now on.
- **On failure:** The page is shown in the chosen language, but the employee's stored language keeps its previous value (AF-1).

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Exactly four languages are offered: English (en), German (de), Spanish (es) and French (fr); English is the last fallback |
| BR-02 | Switching takes effect immediately on all texts, dates, times and numbers, without reloading the data of the page and without signing the user out |
| BR-03 | The language shown when a page opens is decided in this order: the signed-in employee's stored language, the language chosen in the browser, the language of the browser if it is one of the four, English |
| BR-04 | The stored language lives in a separate table of employee settings, not in the employee table: one row per employee (created the first time a setting is saved), one column per setting, empty meaning "not chosen, use the default". The language is the first setting; further settings such as a theme are added later as further columns. The database only accepts the four language codes |
| BR-05 | Only the employee changes their own language; administrators do not set it for others, and the employee management form does not contain it |
| BR-06 | Emails (timesheet submitted, resubmitted, approved, rejected) are sent in the recipient's language; without a stored language the configured default (`stubu.notifications.locale`) is used |
| BR-07 | Every text exists in all four languages. A missing text falls back to English and never shows a raw key. Tests check that all four sets of texts have the same keys and the same placeholders |
| BR-08 | Data entered by users (holiday names, reasons, names) and the values stored in the audit log are not translated; the language of the page and the format of dates and numbers follow the chosen language, the time zone does not |
| BR-09 | The selector is accessible: it has a translated label, can be used with the keyboard, each option is marked with its own language, and the flag is decoration next to the name, never the only information |
| BR-10 | Flags are drawn as images so that they appear on every operating system (emoji flags do not appear on Windows) |
| BR-11 | Changing the language is not an audited business change: it is a personal setting and is not written to the audit log |

---

## Tests

> Tests verify the flows and business rules above. There is no separate acceptance-criteria list — the flows and rules *are* the acceptance criteria. The use case's test class, folder, and naming conventions are defined by the `/use-case-tests` skill — do not name a test class here.

- [x] Main Flow covered (steps 1–6), including the order in which the language is chosen when a page opens
- [x] AF-1 (Saving fails) covered
- [x] AF-2 (Choice on the login page, then sign-in) covered
- [x] AF-3 (Unsupported language) covered
- [x] AF-4 (Same language again) covered
- [x] BR-01–BR-11 covered, including: all four sets of texts have the same keys and placeholders, emails in the recipient's language, the settings table constraint
- [x] Layout of the selector checked at desktop, tablet and phone size, on the login page and in the header

---

## UI Surface

- **Language selector in the header:** A dropdown next to the name of the signed-in user on every application page. Closed, it shows the flag and the name of the current language (only the flag on phones, with the name available to screen readers); open, it lists English, Deutsch, Español and Français, each with its flag, the current one marked.
- **Language selector on the login page:** The same dropdown in the corner of the login card, so people can choose before they sign in.
- **Feedback:** The page switches at once; only when saving to the settings fails a message explains that the choice applies to this device only.

| Page | Access |
|------|--------|
| Language selector in the header of the application pages | Authenticated |
| Language selector on the login page | Anonymous |
