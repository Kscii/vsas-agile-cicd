# VSAS - Virtual Scroll Archive System

## Overview

VSAS (Virtual Scroll Archive System) is a Java-based CLI application designed to manage digital documents (scrolls) with features including user registration, authentication, upload/download functionality, bookmarking, and administrative controls.

## Features

- User registration and authentication with secure password hashing
- Role-based access control (user and admin roles)
- Upload and download scroll binaries with metadata
- Scroll lifecycle management (create, read, update, delete)
- Personal bookmarking system
- Advanced filtering and search capabilities
- Preview functionality for scroll content
- Administrative user and statistics management
- Interactive shell and direct command-line execution modes

## Requirements

- Java 17 or higher
- Gradle 8.7 or higher (included via wrapper)

## Installation

### Running Pre-built JAR (Recommended)

Download the latest `vsas-version.jar` from the releases and run:

```bash
java -jar vsas-version.jar
```

This is the recommended way to run the application.

### Building from Source

Clone the repository and build using Gradle:

```bash
git clone https://github.sydney.edu.au/SOFT2412-COMP9412-2025s2/A3-T28-G03.git
cd A3-T28-G03
./gradlew shadowJar
```

The executable JAR will be created at `app/build/libs/app-all.jar`.

After building, run the application using:

```bash
java -jar app/build/libs/app-all.jar
```

## Usage

### Interactive Mode

Launch the application without arguments to enter interactive mode:

```bash
java -jar app-all.jar
```

You will see:

```
Welcome to VSAS CLI. Type 'help' or 'help <command>' for assistance.

vsas>
```

Type commands at the prompt. Use `exit` or `quit` to terminate the session.

### Command-Line Mode

Execute commands directly:

```bash
java -jar app-all.jar <command> [arguments]
```

Example:

```bash
java -jar app-all.jar list --uploader-id U-100
```

## Available Commands

### Authentication

#### register

Register a new user account.

```bash
register --username <u> --email <e> --phone <ph> --id-key <k> [--role <role>]
```

Example:

```bash
register --username alice --email alice@example.com --phone 0400000000 --id-key U-100
```

#### login

Log in with username and password.

```bash
login --username <u>
```

Example:

```bash
login --username alice
```

If password is omitted, you will be prompted securely.

#### logout

Log out of the current session.

```bash
logout
```

#### whoami

Display the current authenticated user or guest status.

```bash
whoami
```

### Scroll Management

#### list

List scroll metadata with optional filters.

```bash
list [--uploader-id <id>] [--scroll-id <sid>] [--name <kw>] [--from <yyyy-MM-dd>] [--to <yyyy-MM-dd>]
```

Example:

```bash
list --uploader-id U-100 --from 2025-01-01
```

#### upload

Upload a scroll binary and metadata. Requires authentication.

```bash
upload --id <sid> --name <name> --file <path>
```

Example:

```bash
upload --id S-001 --name "Quarterly Report" --file ./report.pdf
```

#### download

Download a scroll to the local filesystem. Requires authentication.

```bash
download --id <sid> [--out <dir>]
```

Example:

```bash
download --id S-001 --out ./downloads
```

#### preview

Preview metadata and a snippet of a scroll.

```bash
preview --id <sid>
```

Example:

```bash
preview --id S-001
```

#### scroll delete

Delete a scroll you uploaded. Requires authentication.

```bash
scroll delete --id <sid> [--yes]
```

Example:

```bash
scroll delete --id S-001 --yes
```

#### scroll update

Update scroll metadata or file. Requires authentication.

```bash
scroll update --id <sid> [--name "<n>"] [--file <path>] [--yes]
```

Example:

```bash
scroll update --id S-001 --name "Updated Name"
```

### Bookmark Management

All bookmark commands require authentication.

#### bookmark add

Add a bookmark for a scroll.

```bash
bookmark add --id <sid>
```

Example:

```bash
bookmark add --id S-001
```

#### bookmark list

List bookmarks for the current user.

```bash
bookmark list
```

#### bookmark remove

Remove a bookmark.

```bash
bookmark remove --id <sid> [--yes]
```

Example:

```bash
bookmark remove --id S-001 --yes
```

### Profile Management

#### profile update

Update profile contact details or password. Requires authentication.

```bash
profile update [--email <e>] [--phone <ph>] [--password]
```

Example:

```bash
profile update --email new@example.com --password
```

### Administrative Commands

All administrative commands require admin role.

#### admin users add

Create a user account.

```bash
admin users add --username <u> --id-key <k> --role <user|admin> [--email <e>] [--phone <ph>]
```

Example:

```bash
admin users add --username alice --id-key U-100 --role admin --email alice@example.com
```

#### admin users list

List users with optional filters.

```bash
admin users list [--username <u>] [--id-key <k>] [--role <admin|user>]
```

Example:

```bash
admin users list --role admin
```

#### admin users delete

Delete a user account.

```bash
admin users delete --username <u> [--yes]
```

Example:

```bash
admin users delete --username alice --yes
```

#### admin users role

Update a user's role.

```bash
admin users role --username <u> --role <admin|user>
```

Example:

```bash
admin users role --username alice --role admin
```

#### admin stats

Show scroll usage statistics.

```bash
admin stats [--by uploader]
```

Example:

```bash
admin stats --by uploader
```

### Help

#### help

Show help for commands.

```bash
help [<command> [<subcommand>...]]
```

Examples:

```bash
help
help upload
help scroll delete
```

## Development

### Building and Running

To build the project and run the application:

```bash
./gradlew shadowJar
java -jar app/build/libs/app-all.jar
```

For development purposes, you can also run directly from source using Gradle:

```bash
./gradlew run
```

Note: It is recommended to use `java -jar` with the built JAR for production use.

### Testing

Run the test suite and generate coverage reports:

```bash
./gradlew test jacocoTestReport
```

Reports are available at:

- Test results: `app/build/reports/tests/test/index.html`
- Coverage report: `app/build/reports/jacoco/test/html/index.html`

### Code Style

This project uses Google Java Format for code formatting.

Format code locally:

```bash
./gradlew googleJavaFormat
```

Verify formatting (runs automatically with `check`):

```bash
./gradlew verifyGoogleJavaFormat
```

## Build

Build the standalone executable JAR:

```bash
./gradlew shadowJar
```

The output will be at `app/build/libs/app-all.jar`.

## License

MIT © 2025 SOFT2412-COMP9412-2025s2. See [LICENSE](LICENSE) for details.
