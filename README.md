# PlayWrightJava

A BDD Playwright test automation framework in **Java**, covering **UI login testing** (real browser) and **API testing** (real HTTP) in one codebase, using a clean layered architecture.

This is the Java counterpart to the TypeScript [`BDDPlayWright`](../BDDPlayWright) framework — same targets, same scenarios, same layering, different stack.

---

## Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Getting Started](#getting-started)
- [Running Tests](#running-tests)
- [Project Structure](#project-structure)
- [Writing Tests](#writing-tests)
- [Configuration](#configuration)
- [Reporting](#reporting)
- [CI/CD](#cicd)
- [Troubleshooting](#troubleshooting)

---

## Features

| Feature | Description |
|---|---|
| BDD / Gherkin | Human-readable `.feature` files describing behaviour |
| UI Testing | Real-browser login tests via Playwright (Chromium) |
| API Testing | HTTP request tests with no browser overhead |
| Page Object Model | Locators and actions encapsulated in page classes |
| Dependency Injection | Cucumber-PicoContainer injects page objects and the API client into steps |
| Scenario Outlines | Data-driven tests via Gherkin `Examples` tables |
| Tagging | `@smoke`, `@regression`, `@positive`, `@negative`, `@ui`, `@api` for selective runs |
| Reporting | HTML, JSON, and JUnit XML reports; screenshot on failure |
| Java 17 | Modern language level, runs on 17 and 21 |
| CI Ready | GitHub Actions workflow included |
| Cross-Platform | Windows, macOS, and Linux |

---

## Tech Stack

- **[Playwright 1.63](https://playwright.dev/java/)** — browser automation and API request context
- **[Cucumber-JVM 7.20](https://github.com/cucumber/cucumber-jvm)** — Gherkin syntax and step definitions
- **[JUnit 5 Platform](https://junit.org/junit5/)** — test discovery and the suite runner
- **Cucumber-PicoContainer** — constructor injection of shared state into step classes
- **AssertJ** — fluent, readable assertions
- **dotenv-java** — environment configuration
- **Maven** — build and dependency management

**Why Cucumber-JVM over a custom runner?** .feature files stay the single source of truth for behaviour, and Cucumber generates the executable tests. No bespoke runner wiring, and the reports are standard.

---

## Architecture

The framework is organised in clear layers, each with a single responsibility:

```
.feature files        ->  What the business wants (Gherkin)
       |
   step definitions   ->  Translation between Gherkin and code
       |
   page objects /     ->  How to interact with UI / API
   api client
       |
   hooks + provider   ->  Lifecycle and dependency injection
       |
   config             ->  Environment, URLs, credentials
```

**Key principle: step definitions stay declarative and locator-free; all selectors live in page objects.**

---

## Getting Started

### Prerequisites

- **JDK 17 or newer** (`java -version`)
  - If `java` is not recognised on Windows, check `C:\Program Files\Java` or `C:\Program Files\Eclipse Adoptium`.
- **Maven 3.9+** (`mvn -version`)
  - Not installed? Either install it, or set `MAVEN_HOME` and use the bundled `run-tests.cmd` wrapper.
- **Git**

### Installation

```powershell
# 1. Clone
git clone https://github.com/satviklabs-boop/PlayWrightJava.git
cd PlayWrightJava

# 2. Download the Playwright browser (one-off, required)
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"

# 3. Create your environment file
copy .env.example .env          # macOS/Linux: cp .env.example .env
```

### Verify the setup

```powershell
mvn test
```

You should see the UI login scenarios pass. API scenarios require quota on the target API (see [Troubleshooting](#troubleshooting)).

---

## Running Tests

| Command | What it does |
|---|---|
| `mvn test` | Run the entire suite |
| `mvn test -Dcucumber.filter.tags="@ui"` | Only UI tests (Chromium) |
| `mvn test -Dcucumber.filter.tags="@api"` | Only API tests |
| `mvn test -Dcucumber.filter.tags="@smoke"` | Only `@smoke` scenarios |
| `mvn test -Dcucumber.filter.tags="@regression"` | Only `@regression` scenarios |
| `mvn test -Dcucumber.filter.tags="@negative"` | Only negative scenarios |
| `mvn test -Dcucumber.filter.tags="@ui and not @negative"` | Tag expressions are supported |
| `mvn test -Psmoke` | Shorthand profile for `@smoke` |
| `mvn test -Pregression` | Shorthand profile for `@regression` |
| `mvn test -Pui` | Shorthand profile for `@ui` |
| `mvn test -Papi` | Shorthand profile for `@api` |

### Advanced examples

```powershell
# Run a single feature file
mvn test -Dcucumber.features=src/test/resources/features/ui/login.feature

# Run by scenario name
mvn test -Dcucumber.filter.name="Successful login"

# Run headful (visible browser)
$env:HEADLESS="false"; mvn test -Dcucumber.filter.tags="@ui"

# Point at a different environment
$env:BASE_URL="https://staging.example.com"; mvn test
```

> `mvn test` runs headless by default.

---

## Project Structure

```
PlayWrightJava/
├── src/test/
│   ├── java/com/satviklabs/
│   │   ├── api/
│   │   │   └── ApiClient.java          # HTTP wrapper (GET/POST/PUT/PATCH/DELETE)
│   │   ├── config/
│   │   │   └── ConfigLoader.java       # Central config loader
│   │   ├── hooks/
│   │   │   └── Hooks.java              # Cucumber hooks + world state
│   │   ├── pages/
│   │   │   ├── BasePage.java           # Shared page helpers
│   │   │   └── LoginPage.java          # Login page object
│   │   ├── runner/
│   │   │   └── RunCucumberTest.java    # JUnit 5 suite entry point
│   │   ├── steps/
│   │   │   ├── LoginSteps.java         # UI step definitions
│   │   │   └── ApiSteps.java           # API step definitions
│   │   └── support/
│   │       └── PlaywrightProvider.java # Shared Playwright driver lifecycle
│   └── resources/
│       ├── features/
│       │   ├── ui/login.feature        # UI login scenarios
│       │   └── api/auth-api.feature    # API scenarios
│       ├── config.properties           # Committed defaults
│       └── logback-test.xml            # Logging configuration
│
├── tools/
│   ├── show-results.ps1                # Summarise the last run
│   └── show-failures.ps1               # Show only failed/undefined steps
│
├── .github/workflows/
│   └── playwright-java.yml             # CI pipeline
│
├── pom.xml                             # Maven build
├── run-tests.cmd                       # Windows helper (works without Maven on PATH)
├── .env.example                        # Environment template
└── README.md
```

---

## Writing Tests

### 1. Write the feature file

Business-readable Gherkin in `src/test/resources/features/ui/` or `.../api/`:

```gherkin
@ui @login
Feature: User login

  Background:
    Given the login page is open

  @smoke @positive
  Scenario: Successful login with valid credentials
    When I login with valid credentials
    Then I should be logged in successfully
    And the success message should be displayed
```

### 2. Implement the steps

In `src/test/java/com/satviklabs/steps/`:

```java
@When("I login with valid credentials")
public void iLoginWithValidCredentials() {
    page().loginWithValidCredentials();
}
```

Note that the step is **declarative**: it says *what* happens, not *how*. The how lives in the page object.

### 3. Add page objects as needed

In `src/test/java/com/satviklabs/pages/`, extend `BasePage` and keep all locators private:

```java
public class DashboardPage extends BasePage {

    private final Locator welcomeBanner = page.locator(".welcome");

    public DashboardPage(Page page) {
        super(page);
    }

    public void open() {
        gotoPath("/dashboard");
    }
}
```

Then expose it from `Hooks` (`loginPage()` follows this pattern) so steps can request it.

### Using pre-defined helpers

Every scenario can request these without any setup:

| Helper | Type | Purpose |
|---|---|---|
| `page()` | Playwright `Page` | Browser page (launched lazily) |
| `loginPage()` | `LoginPage` | UI login page object |
| `apiClient()` | `ApiClient` | Configured API client (no browser) |
| `ConfigLoader` | static | URLs, credentials, timeouts |

---

## Configuration

All configuration flows through `.env` → `ConfigLoader` → the code. Nothing hard-codes a URL.

| Variable | Default | Description |
|---|---|---|
| `ENV` | `dev` | Environment name |
| `BASE_URL` | `https://the-internet.herokuapp.com` | UI base URL |
| `UI_USERNAME` | `tomsmith` | Valid UI username |
| `UI_PASSWORD` | `SuperSecretPassword!` | Valid UI password |
| `API_BASE_URL` | `https://reqres.in` | API base URL |
| `API_KEY` | `reqres-free-v1` | Value sent as the `x-api-key` header |
| `API_USERNAME` | `eve.holt@reqres.in` | API login email |
| `API_PASSWORD` | `cityslicka` | API login password |
| `HEADLESS` | `true` | Run browsers headless |
| `SLOW_MO` | `0` | Slow down actions (ms) |
| `DEFAULT_TIMEOUT` | `30000` | Test timeout (ms) |
| `ARTIFACT_DIR` | `target/artifacts` | Where failure screenshots are written |

**Resolution order** (first match wins):

1. JVM system property — `-DBASE_URL=...`
2. Environment variable — `$env:BASE_URL="..."`
3. `.env` file in the project root
4. `src/test/resources/config.properties`
5. Built-in default in `ConfigLoader`

`.env` is git-ignored. Never commit real credentials — use CI secrets in production.

---

## Reporting

After a run you get:

- **HTML report** — `target/cucumber-report.html` (open in a browser)
- **JSON results** — `target/cucumber-report.json`
- **JUnit XML** — `target/cucumber-report.xml` (for CI dashboards)
- **On failure** — a full-page screenshot in `target/artifacts/`, also attached to the scenario

Two helper scripts make triage quick:

```powershell
# One line per scenario with a PASS/FAIL verdict
powershell -ExecutionPolicy Bypass -File tools/show-results.ps1

# Only the failed or undefined steps, with the assertion message
powershell -ExecutionPolicy Bypass -File tools/show-failures.ps1
```

---

## CI/CD

`.github/workflows/playwright-java.yml` runs the full suite on every push and pull request to `main`, across **JDK 17 and 21**:

1. Checkout the repository
2. Set up the JDK with Maven caching
3. Run `mvn -B clean test`
4. Upload the Cucumber reports as artifacts
5. Upload failure screenshots when the build fails

Reports are downloadable from the **Actions** tab of any workflow run. Credentials can be supplied as repository **secrets** (`UI_USERNAME`, `UI_PASSWORD`, `API_KEY`) — the workflow falls back to the defaults when they are absent.

---

## Troubleshooting

**`java` not recognised (Windows)**
The JDK may not be on `PATH`. Check `C:\Program Files\Java`. Either add the `bin` directory to `PATH` or set `JAVA_HOME`:
```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
```

**`mvn` not recognised (Windows)**
Maven is not installed or not on `PATH`. Either install Maven, or set `MAVEN_HOME` and use the wrapper:
```powershell
$env:MAVEN_HOME="C:\path\to\apache-maven-3.9.9"
.\run-tests.cmd test
```

**Browser not found**
Install the Playwright browser once after cloning:
```powershell
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"
```

**BDD steps not picked up**
Confirm the glue path covers your step package. `RunCucumberTest` declares:
`com.satviklabs.steps,com.satviklabs.hooks`. New step packages must be added there.

**API tests fail with 429**
The default target (`reqres.in`) allows **40 anonymous requests per day per IP**. The framework detects this and fails with an explicit message, since it is an environment limit rather than a test defect. Fix: register a free key at [reqres.in](https://app.reqres.in/sign-up), put it in `.env` as `API_KEY`, or wait for the daily reset (midnight UTC).

**API tests fail with 401/403**
Your API key has expired or is missing. Set a fresh `API_KEY` in `.env`.

**API tests fail with 404**
`reqres.in` occasionally changes which demo resources exist. Check the current [documentation](https://app.reqres.in/documentation) and adjust the paths in `auth-api.feature`.

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

## Author

**Satvik Labs** — test automation practice project
