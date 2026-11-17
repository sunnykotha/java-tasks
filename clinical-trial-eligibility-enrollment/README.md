# Clinical Trial Eligibility and Enrollment

A Java-based application for evaluating clinical trial eligibility and managing participant enrollment.

## Features

- **Eligibility checks:** Evaluate whether a patient meets the configured clinical trial eligibility criteria.
- **Enrollment management:** Support enrolling eligible participants in clinical trials.
- **Capacity management:** Manage enrollment limits for trials.
- **Randomization:** Support participant randomization as implemented by the application.
- **Participant withdrawal:** Support withdrawal from a trial.
- **Transactional processing:** Protect supported enrollment operations through transactional processing.

## Technology

- Java
- Maven
- JUnit

## Prerequisites

- Install the Java version required by the project.
- Use the Maven Wrapper included in the repository, if available.

## Run the tests

From the project root, run the following command in PowerShell:

```powershell
.\mvnw.cmd clean test
```

The test suite exercises the application behavior and eligibility and enrollment rules implemented in the project.

## Project structure

- `src/main/java/` - Application source code.
- `src/test/java/` - Automated tests.
- `pom.xml` - Maven project configuration.

## Scope

This project implements clinical trial eligibility and enrollment workflows. Actual eligibility decisions must follow the trial criteria configured for the application.
