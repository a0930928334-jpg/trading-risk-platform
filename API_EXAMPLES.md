# API Examples

## Current Available Endpoint

### Health Check

```http
GET /actuator/health
```

Example response:

```json
{
  "status": "UP"
}
```

## Planned APIs

### Authentication

```http
POST /api/auth/login
```

### Account

```http
GET /api/accounts/{accountId}
```

### Orders

```http
POST /api/orders
GET /api/orders/{orderId}
```

### Trades

```http
GET /api/trades
```

### Portfolio

```http
GET /api/portfolio/{accountId}
```

These APIs are planned for later development phases.
