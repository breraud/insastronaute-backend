# Contributing

Report reproducible bugs and propose focused improvements through this repository's
issues and pull requests. Check existing issues before opening a new one.

## Development

Install JDK 25 and clone the repository. Run the test suite and build the application:

```bash
./gradlew test bootJar
```

Tests use H2. For a running API, follow the README configuration instructions and
provide PostgreSQL, Redis, Keycloak and S3-compatible storage. Keep local secrets in
ignored configuration files or environment variables.

## Changes

Create a branch for each change and open a pull request describing the problem,
the resulting behavior, and the checks performed. Keep changes scoped and include
focused regression coverage for behavior changes.

Follow existing Java package boundaries, use DTOs at API boundaries, preserve role
checks and CSRF protection, and add a new Flyway migration for schema changes.
Update the README or API flow documentation when behavior or configuration changes.

Include reproduction steps and relevant sanitized error messages in bug reports.
Never attach credentials, session cookies, private user data or production logs.

## License

Contributions are offered under the project's [PolyForm Noncommercial 1.0.0
license](LICENSE). Preserve third-party licenses and notices.
