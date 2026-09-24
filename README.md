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
- [Extent Reports](#extent-reports)
- [Retry analyser](#retry-analyser)
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
| Extent Reports | Rich HTML dashboard with per-step detail, failure screenshots inlined, and environment info |
| Retry analyser | Failed scenarios are re-run; flaky passes are reported, genuine failures still fail the build |
| CI Ready | GitHub Actions workflow **and** a Jenkins pipeline included |
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
| `mvn -Pretry test` | Run the suite **with the retry analyser** (failed scenarios are re-run) |
| `mvn -Pretry test -DRETRY_MAX_ATTEMPTS=2` | Retry analyser with up to 2 extra attempts per failure |
| `mvn -Pretry test -DRETRY_ENABLED=false` | Retry analyser present but disabled |

### Advanced examples

```powershell
# Run by scenario name
mvn test -Dcucumber.filter.name="Successful login"

# Run headful (visible browser)
$env:HEADLESS="false"; mvn test -Dcucumber.filter.tags="@ui"

# Point at a different environment
$env:BASE_URL="https://staging.example.com"; mvn test
```

> `mvn test` runs headless by default.

### Running the quota-free API check

The default API target, `reqres.in`, allows only **40 anonymous requests per day**.
Once that quota is gone, every API scenario fails with **HTTP 429**:

```
The API returned HTTP 429 (rate limited) instead of 200.
This is an environment limitation, not a defect in the test.
```

To prove the API layer itself works, run the smoke check against a target with no
quota:

```powershell
tools\run-api-smoke.cmd
```

which is equivalent to:

```powershell
mvn test -Dcucumber.filter.tags="@api-smoke" -DAPI_BASE_URL=https://jsonplaceholder.typicode.com
```

> **Do not** narrow a run with `-Dcucumber.features=...`. This suite discovers
> features from the classpath, and setting that property makes Cucumber ignore all
> other discovery selectors, failing with
> `TestEngine with ID 'cucumber' failed to discover tests`.
> Select with **tags** instead.

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
| `API_BASE_URL` | `https://jsonplaceholder.typicode.com` | API base URL |
| `API_KEY` | *(empty)* | Optional value sent as the `x-api-key` header |
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
### Extent Reports

Alongside the Cucumber output, the framework produces an **Extent Reports** dashboard at:

```
target/extent-report/Index.html
```

It is a single self-contained HTML file — open it directly in a browser, no server needed. Failure screenshots are inlined as base64, so the report still shows them after being downloaded as a CI artefact or emailed.

See [Extent Reports](#extent-reports) for what it contains and how to configure it.

### Retry artefacts

When the retry analyser runs, it also writes to `target/retry/`:

- **`retry-report.txt`** — a human-readable verdict: which scenarios were *flaky* (failed then passed) and which are *still failing*
- **`retry-ledger.json`** — the per-scenario attempt history across passes
- **`rerun.txt`** — the Cucumber rerun file listing scenarios queued for another attempt

Two helper scripts make triage quick:

```powershell
# One line per scenario with a PASS/FAIL verdict
powershell -ExecutionPolicy Bypass -File tools/show-results.ps1

# Only the failed or undefined steps, with the assertion message
powershell -ExecutionPolicy Bypass -File tools/show-failures.ps1
```

---

## Extent Reports

The framework produces a rich **Extent Reports** HTML dashboard in addition to the plain Cucumber output.

```
target/extent-report/Index.html
```

It is **one self-contained HTML file**. Double-click it — no server, no sibling asset folder, no plugin. Two things make that work:

- **Failure screenshots are inlined as base64**, so an image is still visible after the report is zipped, downloaded from CI, or emailed. A path-based image would break the moment the file moved.
- `offlineMode` is `false`, so the shared CSS/JS come from a CDN and the file itself stays small.

### What is in it

| Panel | Shows |
|---|---|
| Dashboard | Pass/fail totals, duration, and the environment table |
| Timeline | When each scenario ran, so a slow suite becomes obvious |
| Tests tree | Scenarios grouped by feature, each with a green **Pass** / red **Fail** badge and its duration |
| Scenario detail | The Gherkin description, then every step with its own status |
| Failure detail | The exception, the **page URL at the moment of failure**, and the screenshot |
| Category view | Scenarios grouped by tag (`@ui`, `@api`, `@regression`, …) |

### Configuration

Two files, and it matters which is which:

| File | Controls |
|---|---|
| `src/test/resources/extent.properties` | Which reporters run, the output path, and the environment rows |
| `src/test/resources/spark-config.xml` | Title, theme, encoding, and which dashboard widgets appear |

Both **must stay on the test class path** (`src/test/resources`). Rename either and the adapter silently falls back to defaults in the working directory.

> **Worth knowing:** the Cucumber adapter does *not* read `document.title`, `theme` or `encoding` from `extent.properties`. Those live in the XML, referenced by `extent.reporter.spark.config`. Putting them in the properties file has no effect — this is verified against the adapter source, and the comments in both files say so.

The one setting that is easy to miss: the plugin needs an **output directory** appended to its name in the runner.

```java
+ "com.aventstack.extentreports.cucumber.adapter.ExtentCucumberAdapter:target/extent-report"
```

Leave that suffix off and Cucumber aborts the entire run with *"You must supply an output argument"* — no scenarios execute.

### Changing the theme

Edit `<theme>` in `spark-config.xml`: `STANDARD` (light) for printing, `DARK` for a CI dashboard.

### Why the adapter, and not custom listener code

The Extent adapter is registered as an ordinary Cucumber plugin, so it consumes the standard Gherkin events rather than being hand-wired. That means:

- **No listener code to maintain** when Cucumber changes its internals
- Steps, tags and statuses appear automatically — nothing to log by hand
- Any `scenario.attach(bytes, "image/png", name)` in a hook becomes a picture on the failing step

`Hooks.java` does exactly that on failure, and attaches the page URL as a log line first so you can see where the browser was when it broke.

---
## Retry analyser

Flaky tests are a fact of life in UI automation: a slow redirect, a cold browser start, a momentary network blip. Re-running the whole suite to find out whether a failure was real wastes time; ignoring failures hides defects. The retry analyser solves both problems.

### Why not just Surefire's `rerunFailingTestsCount`?

Because **it does not work with Cucumber.** Surefire's rerun feature understands JUnit test *methods*, but Cucumber reports one JUnit Platform test per *scenario* through its own engine. Setting `rerunFailingTestsCount` on a Cucumber suite silently does nothing. This framework implements the equivalent properly, at the scenario level.

### How it works

```
mvn -Pretry test
        |
        v
RetrySuiteRunner  -----> pass 0: full suite (RetryListener records every outcome)
        |                        |
        |                        +--> target/retry/retry-ledger.json   (attempt history)
        |                        +--> target/retry/rerun.txt          (failed scenarios)
        |
        +---> pass 1: re-run ONLY those scenarios  (cucumber.features=@rerun.txt)
        |
        +---> repeat until every scenario passes or its attempts are used up
        |
        v
exit 0  if nothing is still failing   (a flaky pass does NOT fail the build)
exit 1  if a scenario fails even after every retry
```

The **ledger** is what makes it terminate safely: a scenario that fails on every attempt stops being re-run once `RETRY_MAX_ATTEMPTS` is exhausted, then counts as a genuine failure. A scenario that fails once and then passes is recorded as **FLAKY**, so a retry can never quietly turn a real defect green.

The ledger is wiped at the start of every run, so a stale file from a previous build can never suppress a failure.

### Flaky vs failed

The whole point of the analyser is to keep this distinction visible:

| Outcome | Meaning | Build result |
|---|---|---|
| Passed first time | Healthy | ✅ Success |
| Failed, then **passed on retry** | **FLAKY** — reported explicitly | ✅ Success, with a warning naming the scenario |
| Failed every attempt | **Genuine failure** | ❌ Failure |

Flaky scenarios are logged loudly (`FLAKY  [feature:.../login.feature]/[scenario:12] ...`) and listed in `target/retry/retry-report.txt`.

### Excluding a scenario from retries

Tag it `@no-retry`. This is right for deliberately-negative tests and for checks where the first failure is already the signal you care about:

```gherkin
@regression @negative @no-retry
Scenario: An unknown path is not found
  When I GET the "/this-does-not-exist" endpoint
  Then the response status should be 404
```

The default exclude tag is `@no-retry`; change it with `RETRY_TAG_EXCLUDE`.

### Commands

| Command | What it does |
|---|---|
| `mvn -Pretry test` | Full suite, then re-run failures (default: 1 extra attempt) |
| `mvn -Pretry test -DRETRY_MAX_ATTEMPTS=2` | Up to 2 extra attempts per failed scenario |
| `mvn -Pretry test -DRETRY_ENABLED=false` | Run once, no retries |
| `mvn -Pretry test "-Dcucumber.filter.tags=@regression"` | Retry analyser over a tagged subset |

`mvn test` (without `-Pretry`) still behaves exactly as before: it runs the suite once. The analyser is opt-in, so nothing changes for day-to-day work.

### Implementation notes

| Class | Responsibility |
|---|---|
| `RetryConfig` | Reads the `RETRY_*` settings through `ConfigLoader` |
| `RetryListener` | JUnit Platform listener; records each scenario outcome (auto-registered via `META-INF/services`) |
| `RetryAnalyzer` | The ledger: attempt counts, flaky detection, rerun file, report |
| `RetrySuiteRunner` | Owns the run → analyse → rerun loop and the final exit code |

---

## CI/CD

Two pipelines are configured.

### GitHub Actions

`.github/workflows/playwright-java.yml` runs the full suite on every push and pull request to `main`, across **JDK 17 and 21**:

1. Checkout the repository
2. Set up the JDK with Maven caching
3. Run `mvn -B clean test`
4. Upload the Cucumber reports, the **Extent Report** and the failure screenshots as artifacts

Reports are downloadable from the **Actions** tab of any workflow run. Credentials can be supplied as repository **secrets** (`UI_USERNAME`, `UI_PASSWORD`, `API_KEY`) — the workflow falls back to the defaults when they are absent.

### Jenkins

`Jenkinsfile` is a declarative pipeline that **runs automatically every 3 hours**:

```groovy
triggers {
    cron('H H/3 * * *')
}
```

`H H/3 * * *` means *every third hour, at a Jenkins-chosen minute*. The `H` hashes the value so several jobs do not all fire at exactly the same instant — the recommended way to write a periodic trigger. The workflow also still supports push, pull request and manual "Build with Parameters" runs.

What the pipeline does:

1. Checkout
2. Verify the JDK and Maven toolchain
3. Install the Playwright Chromium browser (cached between builds)
4. Run `mvn -B -Pretry clean test` — **with the retry analyser enabled**
5. Publish the Cucumber report, the **Extent Report** (linked on the build page), the retry report and failure screenshots
6. Mark the build **UNSTABLE** (not FAILED) when the suite reports failures, so the reports are always published and triageable

Pipeline parameters let you steer a run without editing the file: `CUCUMBER_TAGS`, `RETRY_MAX_ATTEMPTS`, `BASE_URL`, `API_BASE_URL` and `HEADLESS`.

**Before it will run, the Jenkins job needs three things:**

- A **JDK 17** and a **Maven 3** tool configured on the controller, named `jdk-17` and `maven-3` (or rename them in the `environment` block)
- The **Cucumber Reports** plugin installed, for the `cucumber` step (the Extent Report uses the core `publishHTML` step, so it needs no plugin of its own)
- **Scan Multibranch Pipeline Triggers** enabled (or the job polled) — Jenkins ignores a `cron` in a `Jenkinsfile` until the job has indexed the branch at least once

Retry artefacts (`target/retry/**`) and failure screenshots (`target/artifacts/**`) are archived on every build, and the retry verdict is printed straight into the build console.

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

**`TestEngine with ID 'cucumber' failed to discover tests`**
The `cucumber.features` property was set, which makes Cucumber ignore every other
discovery selector (including the suite's classpath scanning) and the engine gives
up. Do **not** pass `-Dcucumber.features=...`. The suite discovers from the
classpath, so narrow a run by tag instead:
```powershell
mvn test -Dcucumber.filter.tags="@api-smoke"
```
The tag filter is wired through `maven-surefire-plugin` as a
`configurationParameters` entry, because Cucumber's JUnit Platform engine reads its
settings as JUnit configuration parameters rather than plain system properties.

**Tag filter has no effect / everything is skipped**
Same cause as above: the filter must reach the engine through surefire's
`configurationParameters`, which `pom.xml` sets from the `cucumber.filter.tags`
property (empty by default, meaning "run everything").

**API tests fail with 429**
Only relevant if you switch `API_BASE_URL` to `reqres.in`, which allows **40 anonymous requests per day per IP**. The framework detects this and fails with an explicit message, since it is an environment limit rather than a test defect. Fix: register a free key at [reqres.in](https://app.reqres.in/sign-up), set `API_KEY`, or wait for the daily reset (midnight UTC). The default target (`jsonplaceholder`) has no such quota.

**Step classes fail to instantiate**
Cucumber requires each step class to have a **public no-argument constructor**; PicoContainer satisfies its parameters. Do not replace the constructor with a parameterised-only one.

**API tests fail with 401/403**
Your API key has expired or is missing. Set a fresh `API_KEY` in `.env`.

**API tests fail with 404**
The path does not exist on the target. Note that `jsonplaceholder` only mocks writes (`POST`/`PUT`/`PATCH`/`DELETE`) under `/posts`, `/comments`, `/albums`, `/photos` and `/todos`; other collections are read-only. Adjust the paths in `auth-api.feature` accordingly.


**Extent Report: "You must supply an output argument"** The adapter plugin needs an output directory appended after a colon, e.g. `ExtentCucumberAdapter:target/extent-report`. Without it Cucumber aborts the run before any scenario executes. Check the plugin string in `RunCucumberTest.java`.

**Extent Report has no title / wrong theme** Those are set in `spark-config.xml`, **not** `extent.properties` — the adapter does not read `document.title`, `theme` or `encoding` from the properties file. Point `extent.reporter.spark.config` at the XML (it already does).

**Extent Report title is empty or the branding is missing** `spark-config.xml` is not on the class path, or the path in `extent.reporter.spark.config` is wrong. Both config files must live in `src/test/resources`.

**No Extent Report produced at all** Check that `extent.reporter.spark.start=true` and that the plugin is registered in `RunCucumberTest.java`. The report lands in `target/extent-report/Index.html`.

**Screenshots do not appear in the Extent Report** The adapter only renders an attachment as a picture when its media type is an image. `Hooks.java` attaches with `"image/png"`; a different media type shows as a plain log line instead.
**Retry analyser reports everything as FLAKY** A scenario is flaky when it failed and then passed on a *later* attempt. If everything shows as flaky, the environment is unstable - check the target service before trusting the run. If you want no retries at all, set `RETRY_MAX_ATTEMPTS=0` or `RETRY_ENABLED=false`.

**`mvn -Pretry test` runs the suite twice (or more)** That is the analyser working as designed: pass 0 is the full suite, later passes re-run only the failures. With `RETRY_MAX_ATTEMPTS=1` you see at most two passes. Set `RETRY_MAX_ATTEMPTS=0` for a single pass.

**A scenario I want to fail immediately keeps getting retried** Tag it `@no-retry`. The exclude tag is configurable through `RETRY_TAG_EXCLUDE`.

**Retry report says "still failing" but the build was green** The build fails only when a scenario has used up *every* attempt and still fails. Check `target/retry/retry-report.txt` — the "Still failing" list is authoritative.

**`rerun.txt` is empty after a failed run** Empty is correct when no scenario had attempts left, or when retries are disabled. It is only populated with scenarios queued for another attempt.

**Jenkins ignores the 3-hour cron** Jenkins does not read a `cron` from a `Jenkinsfile` until the job has indexed the branch at least once. Enable *Scan Multibranch Pipeline Triggers* (or poll SCM) so the trigger is registered.

**Jenkins: `tool 'jdk-17'` / `tool 'maven-3'` not found** Those names must exist in *Manage Jenkins → Tools*. Either create them, or change the names in the `environment` block of the `Jenkinsfile`.

**Jenkins: `cucumber` step not recognised** Install the **Cucumber Reports** plugin on the Jenkins controller.
Why not reqres.in by default?** It is a richer demo API (real auth, pagination) and the framework still supports it, but its 40-requests-per-day anonymous cap makes a full suite run fail once exhausted. `jsonplaceholder` needs no key and has no quota, so the suite is green out of the box.

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

## Author

**Satvik Labs** — test automation practice project
