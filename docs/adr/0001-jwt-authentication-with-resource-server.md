# 1. JWT authentication with Spring Security's OAuth2 resource server

- Status: Accepted
- Date: 2026-10-17

## Context

Every endpoint except registration, login, health and API documentation must identify the caller.
The service is a single stateless Spring Boot application with no external identity provider.
Clients are API consumers (scripts, Swagger UI), not a browser session.

## Options

1. **Server-side sessions** (`HttpSession` + cookie). Simple, but the server holds session state,
   CSRF protection is required, and it does not suit a token-based API client.
2. **Hand-written JWT filter** with a third-party library such as jjwt. Full control, but parsing,
   signature and expiry checks, error handling and wiring into the security context are all custom
   security code.
3. **`spring-boot-starter-oauth2-resource-server`** with a local `JwtDecoder`, and Spring Security's
   `NimbusJwtEncoder` to issue tokens.

## Decision

Option 3.

- Tokens are signed with **HS256**. The key comes from the `JWT_SECRET` environment variable; only the
  `dev` profile has a default. The application refuses to start if the key is shorter than 32 bytes.
- Lifetime **1 hour**; claims `sub` (user ID), `iss`, `iat`, `exp`, `email`. Issuer and expiry are validated.
- Sessions are stateless and CSRF protection is disabled (no cookies are used).
- Controllers receive the user ID through a `@CurrentUserId` argument resolver that reads the JWT subject,
  so an account is never selected by a client-supplied ID.
- 401 and 403 responses use the same RFC 9457 problem detail format as the rest of the API.
- **No refresh token** in v0.1.

## Consequences

- Validation is framework code; the project only supplies an encoder and a decoder.
- HS256 means the same secret signs and verifies. That suits one service. If verification moves to other
  services, switch to RS256 so they only need the public key.
- A token cannot be revoked before it expires: a user disabled after login keeps access for up to an hour.
  Refresh tokens with short-lived access tokens, or a deny list, are on the roadmap.
- Clients must log in again after an hour.
