# VSAS - Virtual Scroll Archive System

Portfolio migration of a University of Sydney SOFT2412/COMP9412 team project, presented as evidence of my Java engineering, Scrum delivery, and CI/CD automation work.

VSAS is a Java 17 command-line application for managing binary "scroll" documents. It supports authenticated upload/download workflows, searchable metadata, bookmarks, role-based admin commands, and release-ready packaging through a Jenkins multibranch pipeline.

## Portfolio Focus

- **CI/CD engineering:** designed and maintained a Jenkins pipeline that checks out multibranch/PR builds, verifies Google Java Format, builds with Gradle, runs JUnit 5 tests, publishes JaCoCo XML/HTML coverage, archives build artifacts, and creates tagged GitHub releases from `main`.
- **Agile delivery:** delivered through three Scrum sprints with user stories, review gates, sprint demos, retrospectives, and tagged release snapshots. Sprint reports are archived in [`docs/sprints`](docs/sprints).
- **Java application design:** implemented a command registry/dispatcher CLI, file-backed repositories, salted password hashing, session management, role-based admin controls, scroll lifecycle commands, and focused unit/integration tests.
- **Release discipline:** kept annotated tags, release artifacts, and versioned fat JARs as portfolio evidence after migrating the project from the original Sydney GitHub Enterprise Server repository.

## My Contribution Highlights

Across the sprint reports, my main focus areas were Scrum coordination and the CI/CD/release track:

- acted as Scrum Master across the project, organizing sprint ceremonies and keeping delivery aligned with user stories;
- configured and maintained the Jenkins CI/CD server and pipeline;
- added release automation for semantic tags, GitHub Releases, and versioned JAR assets;
- helped enforce code review rules and CI status checks before merges;
- implemented or contributed to user-facing features including secure registration/password hashing, search/filter workflows, masked password prompts, and admin user management.

## Application Capabilities

VSAS can be run interactively or as direct CLI commands.

- register, login, logout, and inspect the current session;
- upload, list, preview, download, update, and delete scroll binaries;
- filter scroll metadata by uploader, scroll ID, name, and date range;
- bookmark scrolls for authenticated users;
- manage users, roles, and usage statistics as an admin;
- package the app as an executable fat JAR with Gradle Shadow.

## Technology Stack

- **Language/runtime:** Java 17
- **Build:** Gradle wrapper, Gradle application plugin, Shadow JAR
- **Testing:** JUnit 5, 39 test classes, JaCoCo coverage reports
- **Formatting:** Google Java Format Gradle plugin
- **CI/CD:** Jenkins Declarative Pipeline, multibranch builds, release stages, artifact archiving
- **Persistence:** TSV/file-backed repositories for users, scroll metadata, bookmarks, and usage counters
- **Security:** salted SHA-256 password hashing and masked interactive password prompts

## CI/CD Pipeline

The preserved [`Jenkinsfile`](Jenkinsfile) demonstrates the delivery pipeline used during the project:

1. checkout branch or PR source;
2. run sanity checks and prepare an isolated Gradle cache;
3. verify formatting with Google Java Format;
4. build regular and fat JAR artifacts;
5. run JUnit tests and generate JaCoCo XML/HTML coverage;
6. archive JARs and coverage artifacts;
7. on `main`, compute the next semantic version, create an annotated tag, create a GitHub release, and upload `vsas-<version>.jar`.

The original pipeline targeted the Sydney GHES host. In this public portfolio migration, the historical pipeline is intentionally preserved as evidence rather than silently rewritten into a different deployment environment.

## Sprint Evidence

- [Sprint 1 report](docs/sprints/2025_SOFT2412_Sprint_Report_A3-T28-G03_Sprint1.pdf): CLI MVP, registration, login/session flow, salted password hashing, upload/list foundation, Jenkins CI setup.
- [Sprint 2 report](docs/sprints/2025_SOFT2412_Sprint_Report_A3-T28-G03_Sprint2.pdf): profile and scroll lifecycle features, search/filter UX, masked password prompts, code review rules, automated release flow.
- [Sprint 3 report](docs/sprints/2025_SOFT2412_Sprint_Report_A3-T28-G03_Sprint3.pdf): admin command set, bookmarks, coverage publishing, multibranch CI/CD, final release packaging.

## Run Locally

Requirements:

- Java 17 or newer
- Git

Build and test:

```bash
git clone https://github.com/Kscii/vsas-agile-cicd.git
cd vsas-agile-cicd
./gradlew test
./gradlew shadowJar
```

Run the fat JAR:

```bash
java -jar app/build/libs/app-all.jar
```

Run a direct command:

```bash
java -jar app/build/libs/app-all.jar list --name demo
```

Common commands:

```text
help
register --username <name> --email <email> --phone <phone> --id-key <key>
login --username <name>
upload --id <scroll-id> --name <name> --file <path>
list [--uploader-id <id>] [--scroll-id <id>] [--name <keyword>]
download --id <scroll-id> [--out <directory>]
bookmark add --id <scroll-id>
admin users list
```

## Releases

The public repository preserves the migrated release history and version tags from the original coursework repository. The latest migrated release is `V3.8.3`, with versioned JAR assets available from GitHub Releases.

## Roadmap

Next development directions I would take as a portfolio continuation:

- add a GitHub Actions workflow mirroring the Jenkins checks for public-repo visibility;
- move persistence from TSV files to SQLite or PostgreSQL while keeping repository interfaces testable;
- add signed release artifacts, checksums, and SBOM generation;
- package the CLI with a reproducible container image and seeded demo data;
- improve UX with a richer terminal UI and clearer validation messages;
- add a lightweight web/admin dashboard backed by the same service layer;
- preserve PR/project evidence as static portfolio documentation where native GHES PR migration was not available.

## Migration Note

This repository was migrated from Sydney GitHub Enterprise Server to GitHub.com as a public portfolio repository. See [`MIGRATION.md`](MIGRATION.md) for the migration scope, author rewrite notes, and fidelity limitations.
