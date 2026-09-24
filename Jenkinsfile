// ---------------------------------------------------------------------------
// PlayWrightJava - CI/CD pipeline (Jenkins, declarative)
//
// SCHEDULE
//   Runs automatically every 3 hours via cron('H H/3 * * *').
//   The leading "H" values let Jenkins spread the load across the hour rather
//   than firing every build at :00, which is the recommended practice.
//   "H/3" in the hour field means every 3 hours.
//
//   NOTE: scheduled triggers only fire on the branch Jenkins has indexed, and
//   only when "Scan Multibranch Pipeline Triggers" (or the job polling) is
//   configured. Jenkins ignores cron in a Jenkinsfile until the job has scanned
//   at least once.
//
// Also triggers on:
//   - every push (via the repository's webhook / branch indexing)
//   - pull-request builds when the Git plugin is configured for them
//   - manual "Build with Parameters" runs
//
// WHAT IT DOES
//   1. Checks out the repository
//   2. Verifies the toolchain (JDK + Maven)
//   3. Installs the Playwright browser (cached between builds)
//   4. Runs the suite WITH the retry analyser enabled
//   5. Publishes the Cucumber report, retry report and failure screenshots
//   6. Fails the build only on a genuine, non-flaky failure
// ---------------------------------------------------------------------------

pipeline {

    agent any

    options {
        // Keep the last 20 builds and only a week of history.
        buildDiscarder(logRotator(numToKeepStr: '20', daysToKeepStr: '7'))

        // Abort a build stuck for more than 45 minutes.
        timeout(time: 45, unit: 'MINUTES')

        // Fail fast if a previous build of this job is still running.
        disableConcurrentBuilds()

        // Timestamp every console line - invaluable for scheduled builds.
        timestamps()

        // Colour the console output.
        ansiColor('xterm')
    }

    triggers {
        // Every 3 hours, at a Jenkins-chosen minute so all jobs do not collide.
        cron('H H/3 * * *')

        // Re-run whenever the job definition itself changes.
        pollSCM('H/5 * * * *')
    }

    parameters {
        choice(name: 'CUCUMBER_TAGS',
               choices: ['', '@smoke', '@regression', '@ui', '@api'],
               description: 'Optional tag filter. Empty runs the whole suite.')

        string(name: 'RETRY_MAX_ATTEMPTS',
               defaultValue: '1',
               description: 'Extra attempts per failed scenario (0 disables retries).')

        string(name: 'BASE_URL',
               defaultValue: 'https://the-internet.herokuapp.com',
               description: 'UI base URL under test.')

        string(name: 'API_BASE_URL',
               defaultValue: 'https://jsonplaceholder.typicode.com',
               description: 'API base URL under test.')

        booleanParam(name: 'HEADLESS',
                     defaultValue: true,
                     description: 'Run browsers headless.')
    }

    environment {
        // ---- Toolchain --------------------------------------------------
        JAVA_HOME     = tool 'jdk-17'
        MAVEN_HOME    = tool 'maven-3'
        PATH          = "${JAVA_HOME}/bin:${MAVEN_HOME}/bin:${env.PATH}"

        // ---- Execution --------------------------------------------------
        HEADLESS      = "${params.HEADLESS ? 'true' : 'false'}"
        BASE_URL      = "${params.BASE_URL}"
        API_BASE_URL  = "${params.API_BASE_URL}"

        // Retry analyser driven from the pipeline parameters.
        RETRY_ENABLED      = 'true'
        RETRY_MAX_ATTEMPTS = "${params.RETRY_MAX_ATTEMPTS}"
        RETRY_REPORT_DIR   = 'target/retry'

        // Tag filter is empty by default = run everything.
        CUCUMBER_TAGS = "${params.CUCUMBER_TAGS}"

        // Report paths, kept in one place so publish steps stay readable.
        CUCUMBER_HTML = 'target/cucumber-report.html'
        CUCUMBER_JSON = 'target/cucumber-report.json'
        CUCUMBER_XML  = 'target/TEST-cucumber.xml'
        EXTENT_HTML   = 'target/extent-report/Index.html'
    }

    stages {

        stage('Checkout') {
            steps {
                echo '--- Checking out the repository ---'
                checkout scm
                sh 'ls -la'
            }
        }

        stage('Verify toolchain') {
            steps {
                echo '--- Verifying JDK and Maven ---'
                sh 'java -version'
                sh 'mvn -version'
            }
        }

        stage('Install Playwright browser') {
            steps {
                echo '--- Ensuring Chromium is available (cached) ---'
                sh '''
                    if [ ! -d "$HOME/.cache/ms-playwright" ]; then
                        mvn -B -q exec:java \
                            -D exec.mainClass=com.microsoft.playwright.CLI \
                            -D exec.args="install --with-deps chromium"
                    else
                        echo "Playwright browser cache found - skipping download"
                    fi
                '''
            }
        }

        stage('Run suite with retry analyser') {
            steps {
                echo '--- Running the Cucumber suite (retries enabled) ---'
                script {
                    def tagArg = params.CUCUMBER_TAGS?.trim()
                    def retryArg = "-DRETRY_MAX_ATTEMPTS=${params.RETRY_MAX_ATTEMPTS}"

                    if (tagArg) {
                        echo "Applying tag filter: ${tagArg}"
                    } else {
                        echo 'No tag filter - running every scenario'
                    }

                    // The retry profile makes RetrySuiteRunner own the run loop:
                    // full suite, then re-run failures until attempts run out.
                    def mvnCmd = "mvn -B -Pretry clean test ${retryArg}"
                    if (tagArg) {
                        mvnCmd += " \"-Dcucumber.filter.tags=${tagArg}\""
                    }

                    // Never let a non-zero exit abort the pipeline here: the
                    // post block decides the build result after the reports are
                    // published, so a failure is still fully triaged.
                    def status = sh(script: mvnCmd, returnStatus: true)
                    currentBuild.description = "retry=${params.RETRY_MAX_ATTEMPTS} tags=${tagArg ?: 'all'}"
                    if (status != 0) {
                        unstable("Test suite reported failures (exit ${status}) - see the retry report for flaky vs genuine failures")
                    }
                }
            }
        }

        stage('Publish reports') {
            steps {
                echo '--- Publishing Cucumber and retry reports ---'

                // Cucumber HTML report, embedded in the build page.
                // Requires the "Cucumber Reports" plugin on the Jenkins controller.
                cucumber buildStatus: 'UNSTABLE',
                         fileIncludePattern: '**/cucumber-report.json',
                         sortingMethod: 'ALPHABETICAL',
                         trendsLimit: 20

                // Extent Reports: a single self-contained HTML dashboard.
                // Published as a clickable link on the build page (the Cucumber
                // Reports plugin does not understand the Extent format, so the
                // file is surfaced directly and archived below).
                publishHTML(target: [
                    allowMissing         : true,
                    alwaysLinkToLastBuild: true,
                    keepAll              : true,
                    reportDir            : 'target/extent-report',
                    reportFiles          : 'Index.html',
                    reportName           : 'Extent Report'
                ])

                // Surface the retry verdict as a per-build summary line so a
                // flaky run is obvious without opening the console.
                script {
                    def retryReport = 'target/retry/retry-report.txt'
                    if (fileExists(retryReport)) {
                        def lines = readFile(retryReport).readLines()
                        def flaky = lines.find { it.startsWith('Flaky') } ?: 'Flaky (passed on a later attempt): 0'
                        def failing = lines.find { it.startsWith('Still failing') } ?: 'Still failing after all attempts: 0'
                        echo "RETRY SUMMARY -> ${flaky} | ${failing}"
                    }
                }
            }
        }
    }

    post {
        always {
            echo '--- Archiving artefacts ---'

            // Cucumber reports -------------------------------------------------
            archiveArtifacts artifacts: 'target/cucumber-report.html, target/cucumber-report.json, target/TEST-cucumber.xml',
                             allowEmptyArchive: true,
                             fingerprint: true

            // Retry analyser artefacts -----------------------------------------
            archiveArtifacts artifacts: 'target/retry/**',
                             allowEmptyArchive: true,
                             fingerprint: true

            // Extent Reports dashboard -----------------------------------------
            archiveArtifacts artifacts: 'target/extent-report/**',
                             allowEmptyArchive: true,
                             fingerprint: true

            // Failure screenshots ----------------------------------------------
            archiveArtifacts artifacts: 'target/artifacts/**',
                             allowEmptyArchive: true

            // Show the retry verdict in the build console.
            sh '''
                if [ -f target/retry/retry-report.txt ]; then
                    echo "================ RETRY ANALYSER REPORT ================"
                    cat target/retry/retry-report.txt
                    echo "======================================================="
                else
                    echo "No retry report produced (retries may be disabled)."
                fi
            '''
        }

        unstable {
            echo 'Build UNSTABLE: the suite failed. Check the retry report to see whether the failures are flaky (passed on retry) or genuine.'
        }

        failure {
            echo 'Build FAILED: infrastructure or a non-test stage broke.'
        }

        success {
            echo 'Build SUCCESS: every scenario passed, possibly after retries. Flaky scenarios are listed in the retry report.'
        }

        cleanup {
            // Remove the per-build artifacts directory to keep the workspace small.
            deleteDir()
        }
    }
}
