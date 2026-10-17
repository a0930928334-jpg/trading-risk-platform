# 7. RFC 9457 problem details for every error

- Status: Accepted
- Date: 2026-10-17

## Context

Before v0.1, error bodies were ad hoc (`{"message": ...}`), and Spring Security and Spring MVC errors each
had their own format. Clients need one predictable error shape.

## Options

1. Keep a custom `{"message": ...}` body and extend it.
2. Adopt **RFC 9457 problem details** (`application/problem+json`), which Spring Framework 6 supports natively.

## Decision

Option 2.

- Each module keeps its own exception classes. They extend `ApiException`, which carries an HTTP status
  and a stable `code`.
- `GlobalExceptionHandler` (extends `ResponseEntityExceptionHandler`) turns them into `ProblemDetail` with
  `type = urn:problem-type:<code>`, `title`, `status`, `detail`, `instance` and a `code` property.
  Validation failures add an `errors` list of `{field, message}`; Spring MVC's own errors (malformed JSON,
  unsupported method, ...) use the same format; unexpected exceptions return a generic 500 without internals.
- Spring Security's 401 and 403 are written by a custom entry point and access-denied handler in the same
  format, keeping the `WWW-Authenticate: Bearer` header.

## Consequences

- Clients branch on `status` and `code`, and show `detail` to users.
- Existing tests that asserted `$.message` were changed to assert `$.detail` and `$.code`.
- New modules only declare an exception class; the global handler needs no change.
