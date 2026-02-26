import { beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';

// Mock fetch for sync operations
function createMockFetch(responses = []) {
  let callCount = 0;
  return vi.fn(async (url, options) => {
    const response = responses[callCount] || { ok: true, json: async () => ({}) };
    callCount++;
    return response;
  });
}

async function importFreshUseOfflineSync() {
  vi.resetModules();
  return import('../composables/useOfflineSync.js');
}

describe('useOfflineSync basic sync', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
  });

  it('syncs queued operations on reconnect', async () => {
    const mockFetch = createMockFetch([
      { ok: true, json: async () => ({ id: 1, status: 'created' }) },
    ]);
    vi.stubGlobal('fetch', mockFetch);

    const { useOfflineSync } = await importFreshUseOfflineSync();
    const { syncQueue, isSyncing } = useOfflineSync();

    const queue = [
      {
        id: 1,
        type: 'CREATE_MARKER',
        endpoint: '/api/v1/markers',
        method: 'POST',
        data: { time: 123456, bib: '42' },
        timestamp: Date.now(),
      },
    ];

    const result = await syncQueue(queue);

    expect(mockFetch).toHaveBeenCalledWith('/api/v1/markers', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ time: 123456, bib: '42' }),
    });
    expect(result.synced).toHaveLength(1);
    expect(result.failed).toHaveLength(0);
    expect(isSyncing.value).toBe(false);
  });

  it('handles sync failures gracefully', async () => {
    const mockFetch = createMockFetch([
      { ok: false, status: 500, statusText: 'Internal Server Error' },
    ]);
    vi.stubGlobal('fetch', mockFetch);

    const { useOfflineSync } = await importFreshUseOfflineSync();
    const { syncQueue } = useOfflineSync();

    const queue = [
      {
        id: 1,
        type: 'UPDATE_MARKER',
        endpoint: '/api/v1/markers/1',
        method: 'PUT',
        data: { time: 789012 },
        timestamp: Date.now(),
      },
    ];

    const result = await syncQueue(queue);

    expect(result.synced).toHaveLength(0);
    expect(result.failed).toHaveLength(1);
    expect(result.failed[0].error).toContain('500');
  });

  it('syncs operations in queue order', async () => {
    const syncedOps = [];
    const mockFetch = vi.fn(async (url, options) => {
      syncedOps.push({ url, method: options.method });
      return { ok: true, json: async () => ({}) };
    });
    vi.stubGlobal('fetch', mockFetch);

    const { useOfflineSync } = await importFreshUseOfflineSync();
    const { syncQueue } = useOfflineSync();

    const queue = [
      {
        id: 1,
        type: 'OP1',
        endpoint: '/api/v1/markers',
        method: 'POST',
        data: { value: 1 },
        timestamp: Date.now(),
      },
      {
        id: 2,
        type: 'OP2',
        endpoint: '/api/v1/markers/1',
        method: 'PUT',
        data: { value: 2 },
        timestamp: Date.now() + 1,
      },
      {
        id: 3,
        type: 'OP3',
        endpoint: '/api/v1/markers/2',
        method: 'DELETE',
        data: {},
        timestamp: Date.now() + 2,
      },
    ];

    await syncQueue(queue);

    expect(syncedOps).toHaveLength(3);
    expect(syncedOps[0]).toEqual({ url: '/api/v1/markers', method: 'POST' });
    expect(syncedOps[1]).toEqual({ url: '/api/v1/markers/1', method: 'PUT' });
    expect(syncedOps[2]).toEqual({ url: '/api/v1/markers/2', method: 'DELETE' });
  });

  it('stops sync on network error', async () => {
    const mockFetch = vi.fn().mockRejectedValue(new Error('Network error'));
    vi.stubGlobal('fetch', mockFetch);

    const { useOfflineSync } = await importFreshUseOfflineSync();
    const { syncQueue } = useOfflineSync();

    const queue = [
      {
        id: 1,
        type: 'CREATE_MARKER',
        endpoint: '/api/v1/markers',
        method: 'POST',
        data: { time: 123 },
        timestamp: Date.now(),
      },
    ];

    const result = await syncQueue(queue);

    expect(result.synced).toHaveLength(0);
    expect(result.failed).toHaveLength(1);
    expect(result.failed[0].error).toContain('Network error');
  });
});

describe('useOfflineSync conflict resolution', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
  });

  it('detects conflicts (409 status)', async () => {
    const mockFetch = createMockFetch([
      {
        ok: false,
        status: 409,
        statusText: 'Conflict',
        json: async () => ({
          error: 'Version mismatch',
          serverVersion: 'v2.0',
          clientVersion: 'v1.0',
        }),
      },
    ]);
    vi.stubGlobal('fetch', mockFetch);

    const { useOfflineSync } = await importFreshUseOfflineSync();
    const { syncQueue } = useOfflineSync();

    const queue = [
      {
        id: 1,
        type: 'UPDATE_MARKER',
        endpoint: '/api/v1/markers/1',
        method: 'PUT',
        data: { time: 555 },
        timestamp: Date.now(),
        clientVersion: 'v1.0',
      },
    ];

    const result = await syncQueue(queue);

    expect(result.conflicts).toHaveLength(1);
    expect(result.conflicts[0].clientData).toEqual({ time: 555 });
    expect(result.conflicts[0].serverData).toBeUndefined(); // No serverData in this response
  });

  it('applies Last-Write-Wins strategy for conflicts', async () => {
    const mockFetch = createMockFetch([
      {
        ok: false,
        status: 409,
        json: async () => ({
          serverVersion: 'v2.0',
          serverTimestamp: Date.now() - 5000,
        }),
      },
      { ok: true, json: async () => ({ id: 1, version: 'v3.0' }) },
    ]);
    vi.stubGlobal('fetch', mockFetch);

    const { useOfflineSync } = await importFreshUseOfflineSync();
    const { syncQueue } = useOfflineSync();

    const queue = [
      {
        id: 1,
        type: 'UPDATE_MARKER',
        endpoint: '/api/v1/markers/1',
        method: 'PUT',
        data: { time: 999 },
        timestamp: Date.now(),
        conflictStrategy: 'last-write-wins',
      },
    ];

    const result = await syncQueue(queue);

    // With LWW, should retry with force flag
    expect(mockFetch).toHaveBeenCalledTimes(2);
    expect(result.synced).toHaveLength(1);
  });

  it('queues conflicts for manual resolution', async () => {
    const mockFetch = createMockFetch([
      {
        ok: false,
        status: 409,
        json: async () => ({
          serverVersion: 'v2.0',
          serverData: { time: 777 },
        }),
      },
    ]);
    vi.stubGlobal('fetch', mockFetch);

    const { useOfflineSync } = await importFreshUseOfflineSync();
    const { syncQueue } = useOfflineSync();

    const queue = [
      {
        id: 1,
        type: 'UPDATE_MARKER',
        endpoint: '/api/v1/markers/1',
        method: 'PUT',
        data: { time: 888 },
        timestamp: Date.now(),
        conflictStrategy: 'manual',
      },
    ];

    const result = await syncQueue(queue);

    expect(result.conflicts).toHaveLength(1);
    expect(result.conflicts[0].requiresManualResolution).toBe(true);
    expect(result.conflicts[0].clientData).toEqual({ time: 888 });
    expect(result.conflicts[0].serverData).toEqual({ time: 777 });
  });

  it('applies client wins strategy', async () => {
    const mockFetch = createMockFetch([
      {
        ok: false,
        status: 409,
        json: async () => ({ serverVersion: 'v2.0' }),
      },
      { ok: true, json: async () => ({ id: 1 }) },
    ]);
    vi.stubGlobal('fetch', mockFetch);

    const { useOfflineSync } = await importFreshUseOfflineSync();
    const { syncQueue } = useOfflineSync();

    const queue = [
      {
        id: 1,
        type: 'UPDATE_MARKER',
        endpoint: '/api/v1/markers/1',
        method: 'PUT',
        data: { time: 666 },
        timestamp: Date.now(),
        conflictStrategy: 'client-wins',
      },
    ];

    const result = await syncQueue(queue);

    // Should force update with client data
    expect(mockFetch).toHaveBeenCalledTimes(2);
    const secondCall = mockFetch.mock.calls[1];
    expect(secondCall[1].headers['X-Force-Update']).toBe('true');
  });

  it('applies server wins strategy', async () => {
    const mockFetch = createMockFetch([
      {
        ok: false,
        status: 409,
        json: async () => ({
          serverVersion: 'v2.0',
          serverData: { time: 111 },
        }),
      },
    ]);
    vi.stubGlobal('fetch', mockFetch);

    const { useOfflineSync } = await importFreshUseOfflineSync();
    const { syncQueue } = useOfflineSync();

    const queue = [
      {
        id: 1,
        type: 'UPDATE_MARKER',
        endpoint: '/api/v1/markers/1',
        method: 'PUT',
        data: { time: 222 },
        timestamp: Date.now(),
        conflictStrategy: 'server-wins',
      },
    ];

    const result = await syncQueue(queue);

    // Should discard client changes
    expect(result.discarded).toHaveLength(1);
    expect(result.discarded[0].reason).toBe('server-wins');
  });
});

describe('useOfflineSync retry logic', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
  });

  it('retries failed operations with exponential backoff', async () => {
    let attempts = 0;
    const mockFetch = vi.fn(async () => {
      attempts++;
      if (attempts < 3) {
        throw new Error('Network error');
      }
      return { ok: true, json: async () => ({}) };
    });
    vi.stubGlobal('fetch', mockFetch);

    const { useOfflineSync } = await importFreshUseOfflineSync();
    const { syncQueue } = useOfflineSync();

    const queue = [
      {
        id: 1,
        type: 'CREATE_MARKER',
        endpoint: '/api/v1/markers',
        method: 'POST',
        data: { time: 123 },
        timestamp: Date.now(),
        maxRetries: 3,
      },
    ];

    const result = await syncQueue(queue, { enableRetry: true });

    expect(attempts).toBe(3);
    expect(result.synced).toHaveLength(1);
  });

  it('gives up after max retries exceeded', async () => {
    const mockFetch = vi.fn().mockRejectedValue(new Error('Network error'));
    vi.stubGlobal('fetch', mockFetch);

    const { useOfflineSync } = await importFreshUseOfflineSync();
    const { syncQueue } = useOfflineSync();

    const queue = [
      {
        id: 1,
        type: 'CREATE_MARKER',
        endpoint: '/api/v1/markers',
        method: 'POST',
        data: { time: 456 },
        timestamp: Date.now(),
        maxRetries: 2,
      },
    ];

    const result = await syncQueue(queue, { enableRetry: true });

    expect(mockFetch).toHaveBeenCalledTimes(3); // initial + 2 retries
    expect(result.failed).toHaveLength(1);
  });
});
