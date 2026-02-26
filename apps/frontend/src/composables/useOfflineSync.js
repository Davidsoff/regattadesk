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

const DEFAULT_MAX_RETRIES = 3;
const RETRY_DELAYS = [1000, 2000, 4000]; // Exponential backoff in ms

export function useOfflineSync() {
  const isSyncing = ref(false);

  async function syncOperation(operation, options = {}) {
    const { enableRetry = false, retryAttempt = 0 } = options;
    const maxRetries = operation.maxRetries ?? DEFAULT_MAX_RETRIES;

    try {
      const headers = {
        'Content-Type': 'application/json',
      };

      // Add version header if provided
      if (operation.clientVersion) {
        headers['X-Client-Version'] = operation.clientVersion;
      }

      const response = await fetch(operation.endpoint, {
        method: operation.method,
        headers,
        body: operation.method !== 'GET' && operation.method !== 'DELETE' 
          ? JSON.stringify(operation.data) 
          : undefined,
      });

      if (response.ok) {
        const result = await response.json();
        return { success: true, data: result };
      }

      // Handle conflicts (409)
      if (response.status === 409) {
        const conflictData = await response.json();
        return {
          success: false,
          conflict: true,
          status: 409,
          serverVersion: conflictData.serverVersion,
          serverData: conflictData.serverData,
          serverTimestamp: conflictData.serverTimestamp,
          clientData: operation.data,
          operation,
        };
      }

      // Other errors
      return {
        success: false,
        error: `HTTP ${response.status}: ${response.statusText}`,
        status: response.status,
      };
    } catch (err) {
      // Network error - retry if enabled
      if (enableRetry && retryAttempt < maxRetries) {
        const delay = RETRY_DELAYS[retryAttempt] || RETRY_DELAYS[RETRY_DELAYS.length - 1];
        await new Promise(resolve => setTimeout(resolve, delay));
        return syncOperation(operation, { 
          ...options, 
          retryAttempt: retryAttempt + 1 
        });
      }

      return {
        success: false,
        error: err.message,
      };
    }
  }

  async function handleConflict(operation, conflictResult, options = {}) {
    const strategy = operation.conflictStrategy || 'manual';

    switch (strategy) {
      case 'last-write-wins': {
        // Compare timestamps to determine winner
        const clientTime = operation.timestamp;
        const serverTime = conflictResult.serverTimestamp;

        if (clientTime > serverTime) {
          // Client is newer, force update
          return await forceUpdate(operation);
        } else {
          // Server is newer, discard client changes
          return {
            success: false,
            discarded: true,
            reason: 'server-newer',
          };
        }
      }

      case 'client-wins': {
        return await forceUpdate(operation);
      }

      case 'server-wins': {
        return {
          success: false,
          discarded: true,
          reason: 'server-wins',
        };
      }

      case 'manual':
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

  async function forceUpdate(operation) {
    try {
      const response = await fetch(operation.endpoint, {
        method: operation.method,
        headers: {
          'Content-Type': 'application/json',
          'X-Force-Update': 'true',
        },
        body: JSON.stringify(operation.data),
      });

      if (response.ok) {
        const result = await response.json();
        return { success: true, data: result, forced: true };
      }

      return {
        success: false,
        error: `Force update failed: ${response.status}`,
      };
    } catch (err) {
      return {
        success: false,
        error: err.message,
      };
    }
  }

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
          const resolutionResult = await handleConflict(item, syncResult, options);

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
