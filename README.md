# CC28-Team03-A2A3 - VSAS

## Run

./gradlew run

## Test & Coverage

./gradlew test jacocoTestReport

> Reports: `build/reports/tests/test`, `build/reports/jacoco/test/html`

## Code Style (Google Java Format)

Format locally:
`./gradlew googleJavaFormat`

Verify in CI (already wired to `check`):
`./gradlew verifyGoogleJavaFormat`
