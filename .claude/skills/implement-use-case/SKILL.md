---
name: implement-use-case
description: Implement a use case from the spec/ folder. Use when asked to implement, build, or work on a use case.
argument-hint: "[use-case-name or number]"
---

# Use Case Implementation

Implement the use case specified by $ARGUMENTS.

A use case is the unit of work, not a view. A single use case may produce one view, several views, or no UI at all — let the spec drive that, and keep tests grouped per use case as defined by `/use-case-tests`.

## Input

1. The use case document itself (in `spec/use-cases/`)
2. Related use cases
3. Generic specification files in `spec/`
4. Any potential images or other resources provided for the use case

## Implementation

The following steps are mandatory and sequential. **Do not skip or reorder them.** Each step must be completed before proceeding to the next.

### Step 1: Write the code
- Whenever you are even slightly unsure about Vaadin API usage, component behavior, theme variables, styling, or best practices -- use the Vaadin MCP server to look it up before guessing. Do not rely on memory for Vaadin specifics.

### Step 2: Write and run automated tests
- Verify everything that does **not** depend on visual appearance here first -- main flow, alternative flows, business rules, and services. Use the fast browserless / Vitest / `@SpringBootTest` mechanisms; they are far cheaper and faster than driving a browser, so cover as much behaviour as possible this way before touching Playwright.
- Follow the use-case-tests workflow and ensure all tests pass before moving on.

### Step 3: Visually verify with Playwright MCP
- **This step is mandatory.** Do not skip it, do not defer it.
- Behaviour is already covered by the automated tests, so use Playwright MCP **only** to verify what those tests can't: layout, spacing, rendering, and visual appearance.
- Follow the visual-verification workflow end-to-end: start the app, navigate every route, take screenshots, and validate the visual appearance. Don't re-check functional behaviour already covered by Step 2.
- Fix any issues found before moving on.

### Step 4: Iterate
- Keep iterating until everything looks and works great. Prefer great results over finishing quickly.

### Step 5: Commit
- Once all steps are complete and everything works, create a git commit with the changes.

**All steps must be completed before a use case is considered implemented.**
