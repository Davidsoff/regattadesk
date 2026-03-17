/**
 * Offline sync protocol with conflict resolution
 * 
 * Handles syncing queued operations with configurable conflict strategies:
 * - last-write-wins: Use timestamp to determine winner
 * - client-wins: Force client data
 * - server-wins: Discard client changes
 * - manual: Queue for manual resolution
 */

import { ref } from 'vue';
import { safeJsonParse } from '../utils/jsonUtils.js';

const DEFAULT_MAX_RETRIES = 3;
const RETRY_DELAYS = [1000, 2000, 4000]; // Exponential backoff in ms
const ALLOWED_METHODS = new Set(['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS']);
const METHODS_WITH_BODY = new Set(['POST', 'PUT', 'PATCH']);

function hasWindow() {
  return globalThis.window !== undefined;
}

function getCsrfToken() {
  if (typeof document === 'undefined') {
    return null;
  }

  const tokenMeta = document.querySelector('meta[name="csrf-token"]');
  return tokenMeta?.getAttribute('content') || null;
}

function normalizeAndValidateOperation(operation) {
  if (!operation || typeof operation !== 'object') {
    throw new Error('Invalid sync operation');
  }

  if (typeof operation.endpoint !== 'string' || operation.endpoint.length === 0) {
    throw new Error('Operation endpoint is required');
  }

  let endpointUrl;
  try {
    endpointUrl = new URL(
      operation.endpoint,
      hasWindow() ? globalThis.window.location.origin : 'https://example.invalid'
    );
  } catch (err) {
    throw new Error('Operation endpoint is not a valid URL', { cause: err });
  }

  if (!endpointUrl.pathname.startsWith('/api/')) {
    throw new Error('Operation endpoint must target /api/*');
  }

  const method = typeof operation.method === 'string' ? operation.method.toUpperCase() : '';
  if (!ALLOWED_METHODS.has(method)) {
    throw new Error(`Unsupported HTTP method: ${operation.method}`);
  }

  if (operation.data !== undefined) {
    try {
      JSON.stringify(operation.data);
    } catch (err) {
      throw new Error('Operation data must be JSON-serializable', { cause: err });
    }
  }

  return {
    ...operation,
    endpoint: endpointUrl.pathname + endpointUrl.search,
    method,
  };
}

async function parseResponseBody(response) {
  if (response.status === 204 || response.status === 205) {
    return null;
  }

  const text = await response.text();
  if (!text) {
    return null;
  }

  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    return safeJsonParse(text, null);
  }

  const parsed = safeJsonParse(text, undefined);
  if (parsed !== undefined) {
    return parsed;
  }
  return text;
}

function buildRequestHeaders(operation) {
  const headers = {
    'Content-Type': 'application/json',
  };

  if (operation.clientVersion) {
    headers['X-Client-Version'] = operation.clientVersion;
  }

  if (METHODS_WITH_BODY.has(operation.method)) {
    const csrfToken = getCsrfToken();
    if (csrfToken) {
      headers['X-CSRF-Token'] = csrfToken;
    }
  }

  return headers;
}

async function forceUpdateOperation(operation) {
  try {
    const validatedOperation = normalizeAndValidateOperation(operation);
    const response = await fetch(validatedOperation.endpoint, {
      method: validatedOperation.method,
      headers: {
        ...buildRequestHeaders(validatedOperation),
        'X-Force-Update': 'true',
      },
      body: METHODS_WITH_BODY.has(validatedOperation.method)
        ? JSON.stringify(validatedOperation.data ?? null)
        : undefined,
    });

    if (response.ok) {
      const result = await parseResponseBody(response);
      return { success: true, data: result, forced: true };
    }

    return {
      success: false,
      error: `Force update failed: ${response.status}`,
    };
  } catch (err) {
    return {
      success: false,
      error: err instanceof Error ? err.message : String(err),
    };
  }
}

function getRetryDelay(retryAttempt) {
  return RETRY_DELAYS[retryAttempt] || RETRY_DELAYS.at(-1)
}

async function fetchOperation(validatedOperation) {
  return fetch(validatedOperation.endpoint, {
    method: validatedOperation.method,
    headers: buildRequestHeaders(validatedOperation),
    body: METHODS_WITH_BODY.has(validatedOperation.method)
      ? JSON.stringify(validatedOperation.data ?? null)
      : undefined,
  })
}

async function buildSyncResponse(validatedOperation, response) {
  if (response.ok) {
    const result = await parseResponseBody(response)
    return { success: true, data: result }
  }

  if (response.status === 409) {
    const conflictData = await parseResponseBody(response)
    return {
      success: false,
      conflict: true,
      status: 409,
      serverVersion: conflictData?.serverVersion,
      serverData: conflictData?.serverData,
      serverTimestamp: conflictData?.serverTimestamp,
      clientData: validatedOperation.data,
      operation: validatedOperation,
    }
  }

  return {
    success: false,
    error: `HTTP ${response.status}: ${response.statusText}`,
    status: response.status,
  }
}

function shouldRetryRequest(enableRetry, retryAttempt, maxRetries) {
  return enableRetry && retryAttempt < maxRetries
}

async function syncOperation(operation, options = {}) {
  const { enableRetry = false } = options
  let validatedOperation
  try {
    validatedOperation = normalizeAndValidateOperation(operation)
  } catch (err) {
    return {
      success: false,
      error: err instanceof Error ? err.message : String(err),
    }
  }

  const maxRetries = validatedOperation.maxRetries ?? DEFAULT_MAX_RETRIES
  let retryAttempt = options.retryAttempt ?? 0

  while (true) {
    try {
      const response = await fetchOperation(validatedOperation)
      return await buildSyncResponse(validatedOperation, response)
    } catch (err) {
      if (!shouldRetryRequest(enableRetry, retryAttempt, maxRetries)) {
        return {
          success: false,
          error: err instanceof Error ? err.message : String(err),
        }
      }

      const delay = getRetryDelay(retryAttempt)
      retryAttempt += 1
      await new Promise((resolve) => setTimeout(resolve, delay))
    }
  }
}

async function handleConflict(operation, conflictResult) {
  const strategy = operation.conflictStrategy || 'manual';

  switch (strategy) {
    case 'last-write-wins': {
      // Compare timestamps to determine winner
      const clientTime = operation.timestamp;
      const serverTime = conflictResult.serverTimestamp;

      if (clientTime > serverTime) {
        // Client is newer, force update
        return forceUpdateOperation(operation);
      }

      // Server is newer, discard client changes
      return {
        success: false,
        discarded: true,
        reason: 'server-newer',
      };
    }

    case 'client-wins': {
      return forceUpdateOperation(operation);
    }

    case 'server-wins': {
      return {
        success: false,
        discarded: true,
        reason: 'server-wins',
      };
    }

    default: {
      return {
        success: false,
        conflict: true,
        requiresManualResolution: true,
        clientData: conflictResult.clientData,
        serverData: conflictResult.serverData,
        operation: conflictResult.operation,
      };
    }
  }
}

export function useOfflineSync() {
  const isSyncing = ref(false);

  async function syncQueue(queue, options = {}) {
    isSyncing.value = true;

    const result = {
      synced: [],
      failed: [],
      conflicts: [],
      discarded: [],
    };

    try {
      for (const item of queue) {
        const syncResult = await syncOperation(item, options);

        if (syncResult.success) {
          result.synced.push({
            id: item.id,
            operation: item,
            data: syncResult.data,
          });
        } else if (syncResult.conflict) {
          // Try to resolve conflict
          const resolutionResult = await handleConflict(item, syncResult);

          if (resolutionResult.success) {
            result.synced.push({
              id: item.id,
              operation: item,
              data: resolutionResult.data,
              resolved: true,
            });
          } else if (resolutionResult.discarded) {
            result.discarded.push({
              id: item.id,
              operation: item,
              reason: resolutionResult.reason,
            });
          } else if (resolutionResult.requiresManualResolution) {
            result.conflicts.push({
              id: item.id,
              ...resolutionResult,
            });
          } else {
            result.failed.push({
              id: item.id,
              operation: item,
              error: resolutionResult.error,
            });
          }
        } else {
          result.failed.push({
            id: item.id,
            operation: item,
            error: syncResult.error,
            status: syncResult.status,
          });

          // Stop on network errors to avoid cascading failures
          if (!syncResult.status || syncResult.status >= 500) {
            break;
          }
        }
      }
    } finally {
      isSyncing.value = false;
    }

    return result;
  }

  return {
    isSyncing,
    syncQueue,
  };
}
