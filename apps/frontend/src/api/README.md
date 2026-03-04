# RegattaDesk API Client

Shared frontend API client for all RegattaDesk surfaces (staff, operator, public).

## Features

- **OpenAPI-aligned error handling**: Consistent normalization of `error_response` schema
- **Idempotency support**: Pass-through support for OpenAPI `idempotency_key` request field
- **Type-safe domain modules**: Organized by bounded context (Finance, Staff, Operator, Public)
- **Request/response consistency**: Centralized fetch logic with error normalization

## Usage

### Basic Setup

```javascript
import { createApiClient, createFinanceApi } from '@/api'

// Create base client
const apiClient = createApiClient()

// Create domain-specific API modules
const financeApi = createFinanceApi(apiClient)
```

### Making API Calls

```javascript
try {
  const result = await financeApi.markBulkPayment(regattaId, {
    entry_ids: ['uuid-1', 'uuid-2'],
    payment_status: 'paid',
    payment_reference: 'BANK-REF-123',
    idempotency_key: 'unique-key-456'
  })
  
  console.log('Success:', result.message)
  console.log('Updated:', result.updated_count)
} catch (error) {
  // Error is normalized from OpenAPI error_response schema
  console.error('Error code:', error.code)
  console.error('Error message:', error.message)
  console.error('Request ID:', error.requestId)
  console.error('HTTP status:', error.status)
  
  if (error.details) {
    console.error('Details:', error.details)
  }
}
```

### Error Handling

The API client normalizes all errors to this shape:

```typescript
{
  code: string,        // Error code (e.g., 'VALIDATION_ERROR', 'NOT_FOUND')
  message: string,     // Human-readable error message
  details?: object,    // Optional additional error details
  requestId?: string,  // Optional request ID for tracing
  status: number       // HTTP status code
}
```

### Idempotency

For retry-safe operations, include an `idempotency_key` in your payload:

```javascript
const payload = {
  entry_ids: ['uuid-1'],
  payment_status: 'paid',
  idempotency_key: 'my-unique-key'  // Sent as part of the request payload
}

await financeApi.markBulkPayment(regattaId, payload)
```

The finance domain helper passes `idempotency_key` through in the request payload as defined by OpenAPI.

## API Modules

### Finance API

```javascript
import { createFinanceApi } from '@/api'

const financeApi = createFinanceApi(apiClient)

// Mark bulk payment status
await financeApi.markBulkPayment(regattaId, {
  entry_ids: ['uuid-1', 'uuid-2'],
  club_ids: ['uuid-3'],
  payment_status: 'paid',
  payment_reference: 'optional-ref',
  idempotency_key: 'optional-key'
})
```

## Extending the Client

### Adding New Domain Modules

Create a new module in `src/api/`:

```javascript
// src/api/my-module.js
export function createMyModuleApi(client) {
  return {
    async getResource(id) {
      return client.get(`/my-module/${id}`)
    },
    
    async createResource(data) {
      return client.post('/my-module', data)
    }
  }
}
```

Export it from `src/api/index.js`:

```javascript
export { createMyModuleApi } from './my-module.js'
```

### Custom Client Configuration

```javascript
const apiClient = createApiClient({
  baseUrl: '/api/v2'  // Override default '/api/v1'
})
```

## Testing

The API client is fully tested with unit tests in `src/api/__tests__/`.

### Mocking in Component Tests

When testing components that use the API client, mock `fetch`:

```javascript
import { vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'

it('handles API call', async () => {
  const mockResponse = { success: true, message: 'OK' }
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
    ok: true,
    json: async () => mockResponse
  }))
  
  // Trigger component action
  await wrapper.find('button').trigger('click')
  await flushPromises()  // Wait for async operations
  
  // Assert on result
  expect(wrapper.text()).toContain('OK')
})
```

### Error Testing

```javascript
it('handles API errors', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
    ok: false,
    status: 400,
    json: async () => ({
      error: {
        code: 'VALIDATION_ERROR',
        message: 'Invalid input'
      }
    })
  }))
  
  await wrapper.find('button').trigger('click')
  await flushPromises()
  
  expect(wrapper.text()).toContain('Invalid input')
})
```

## OpenAPI Alignment

This client is designed to match the contracts in `pdd/design/openapi-v0.1.yaml`:

- **Error responses**: Conforms to `error_response` schema
- **Auth modes**: Supports Staff (proxy auth), Operator (token), and Public (anonymous)
- **Idempotency**: Handles the `idempotency_key` request field as defined in the spec

### Generated SDK

The repo also includes an auto-generated OpenAPI SDK at `src/api/generated/`.

Regenerate it from the current OpenAPI spec:

```bash
cd apps/frontend
npm run api:generate
```

Verify generated files are up to date:

```bash
cd apps/frontend
npm run api:check
```

`npm run dev` and `npm run build` now run `api:sync` automatically, which:

1. Regenerates `pdd/design/openapi-v0.1.yaml` from backend code.
2. Regenerates `src/api/generated/` from that spec.

## References

- OpenAPI spec: `pdd/design/openapi-v0.1.yaml`
- Implementation plan: `pdd/implementation/plan.md`
- Issue: FEGAP-002
