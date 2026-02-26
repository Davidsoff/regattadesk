import { beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';

// Mock IndexedDB
class MockIDBDatabase {
  constructor() {
    this.stores = new Map();
    this.version = 1;
  }

  transaction(storeNames, mode) {
    return new MockIDBTransaction(this, storeNames, mode);
  }

  createObjectStore(name, options) {
    const store = { name, keyPath: options?.keyPath, autoIncrement: options?.autoIncrement };
    this.stores.set(name, store);
    return store;
  }
}

class MockIDBTransaction {
  constructor(db, storeNames, mode) {
    this.db = db;
    this.storeNames = Array.isArray(storeNames) ? storeNames : [storeNames];
    this.mode = mode;
    this.oncomplete = null;
    this.onerror = null;
    this._stores = new Map();
  }

  objectStore(name) {
    if (!this._stores.has(name)) {
      this._stores.set(name, new MockIDBObjectStore(name));
    }
    return this._stores.get(name);
  }

  complete() {
    if (this.oncomplete) this.oncomplete();
  }
}

class MockIDBObjectStore {
  constructor(name) {
    this.name = name;
    this._data = new Map();
    this._keyCounter = 0;
  }

  add(value) {
    return this._createRequest(() => {
      const key = ++this._keyCounter;
      this._data.set(key, value);
      return key;
    });
  }

  get(key) {
    return this._createRequest(() => this._data.get(key) || undefined);
  }

  getAll() {
    return this._createRequest(() => Array.from(this._data.values()));
  }

  delete(key) {
    return this._createRequest(() => {
      this._data.delete(key);
      return undefined;
    });
  }

  clear() {
    return this._createRequest(() => {
      this._data.clear();
      return undefined;
    });
  }

  _createRequest(operation) {
    const request = {
      onsuccess: null,
      onerror: null,
      result: null,
    };

    setTimeout(() => {
      try {
        request.result = operation();
        if (request.onsuccess) request.onsuccess({ target: request });
      } catch (error) {
        if (request.onerror) request.onerror({ target: { error } });
      }
    }, 0);

    return request;
  }
}

class MockIDBOpenDBRequest {
  constructor(dbName, version) {
    this.dbName = dbName;
    this.version = version;
    this.onsuccess = null;
    this.onerror = null;
    this.onupgradeneeded = null;
    this.result = null;
  }

  trigger() {
    const db = new MockIDBDatabase();
    
    if (this.onupgradeneeded) {
      const event = {
        target: { result: db },
        oldVersion: 0,
        newVersion: this.version,
      };
      this.onupgradeneeded(event);
    }

    this.result = db;
    if (this.onsuccess) {
      this.onsuccess({ target: this });
    }
  }
}

function setupMockIndexedDB() {
  const openRequests = [];
  const mockIndexedDB = {
    open: (name, version) => {
      const request = new MockIDBOpenDBRequest(name, version);
      openRequests.push(request);
      setTimeout(() => request.trigger(), 0);
      return request;
    },
  };

  vi.stubGlobal('indexedDB', mockIndexedDB);
  return { openRequests };
}

async function importFreshUseOfflineQueue() {
  vi.resetModules();
  return import('../composables/useOfflineQueue.js');
}

describe('useOfflineQueue initialization', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
  });

  it('initializes IndexedDB database', async () => {
    setupMockIndexedDB();

    const { useOfflineQueue } = await importFreshUseOfflineQueue();
    const { isReady } = useOfflineQueue();

    await vi.waitFor(() => expect(isReady.value).toBe(true));
  });

  it('handles IndexedDB initialization errors', async () => {
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    
    vi.stubGlobal('indexedDB', {
      open: () => {
        const request = {
          onerror: null,
          onsuccess: null,
          onupgradeneeded: null,
        };
        setTimeout(() => {
          if (request.onerror) {
            request.onerror({ target: { error: new Error('DB open failed') } });
          }
        }, 0);
        return request;
      },
    });

    const { useOfflineQueue } = await importFreshUseOfflineQueue();
    const { isReady, error } = useOfflineQueue();

    await vi.waitFor(() => expect(error.value).toBeTruthy());
    expect(isReady.value).toBe(false);
    
    errorSpy.mockRestore();
  });
});

describe('useOfflineQueue operations', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
  });

  it('enqueues operations when offline', async () => {
    setupMockIndexedDB();

    const { useOfflineQueue } = await importFreshUseOfflineQueue();
    const { enqueue, getQueue, isReady } = useOfflineQueue();

    await vi.waitFor(() => expect(isReady.value).toBe(true));

    const operation = {
      type: 'CREATE_MARKER',
      endpoint: '/api/v1/markers',
      method: 'POST',
      data: { time: 123456, bib: '42' },
      timestamp: Date.now(),
    };

    await enqueue(operation);
    const queue = await getQueue();

    expect(queue).toHaveLength(1);
    expect(queue[0].type).toBe('CREATE_MARKER');
    expect(queue[0].data).toEqual({ time: 123456, bib: '42' });
  });

  it('dequeues operations after processing', async () => {
    setupMockIndexedDB();

    const { useOfflineQueue } = await importFreshUseOfflineQueue();
    const { enqueue, dequeue, getQueue, isReady } = useOfflineQueue();

    await vi.waitFor(() => expect(isReady.value).toBe(true));

    const operation = {
      type: 'UPDATE_MARKER',
      endpoint: '/api/v1/markers/1',
      method: 'PUT',
      data: { time: 789012 },
      timestamp: Date.now(),
    };

    const queueId = await enqueue(operation);
    let queue = await getQueue();
    expect(queue).toHaveLength(1);

    await dequeue(queueId);
    queue = await getQueue();
    expect(queue).toHaveLength(0);
  });

  it('preserves queue order (FIFO)', async () => {
    setupMockIndexedDB();

    const { useOfflineQueue } = await importFreshUseOfflineQueue();
    const { enqueue, getQueue, isReady } = useOfflineQueue();

    await vi.waitFor(() => expect(isReady.value).toBe(true));

    const ops = [
      { type: 'OP1', data: { value: 1 }, timestamp: Date.now() },
      { type: 'OP2', data: { value: 2 }, timestamp: Date.now() + 1 },
      { type: 'OP3', data: { value: 3 }, timestamp: Date.now() + 2 },
    ];

    for (const op of ops) {
      await enqueue(op);
    }

    const queue = await getQueue();
    expect(queue).toHaveLength(3);
    expect(queue[0].type).toBe('OP1');
    expect(queue[1].type).toBe('OP2');
    expect(queue[2].type).toBe('OP3');
  });

  it('clears entire queue', async () => {
    setupMockIndexedDB();

    const { useOfflineQueue } = await importFreshUseOfflineQueue();
    const { enqueue, clearQueue, getQueue, isReady } = useOfflineQueue();

    await vi.waitFor(() => expect(isReady.value).toBe(true));

    await enqueue({ type: 'OP1', data: {}, timestamp: Date.now() });
    await enqueue({ type: 'OP2', data: {}, timestamp: Date.now() });

    let queue = await getQueue();
    expect(queue).toHaveLength(2);

    await clearQueue();
    queue = await getQueue();
    expect(queue).toHaveLength(0);
  });

  it('tracks queue size reactively', async () => {
    setupMockIndexedDB();

    const { useOfflineQueue } = await importFreshUseOfflineQueue();
    const { enqueue, dequeue, queueSize, isReady } = useOfflineQueue();

    await vi.waitFor(() => expect(isReady.value).toBe(true));

    expect(queueSize.value).toBe(0);

    const id1 = await enqueue({ type: 'OP1', data: {}, timestamp: Date.now() });
    await nextTick();
    expect(queueSize.value).toBe(1);

    await enqueue({ type: 'OP2', data: {}, timestamp: Date.now() });
    await nextTick();
    expect(queueSize.value).toBe(2);

    await dequeue(id1);
    await nextTick();
    expect(queueSize.value).toBe(1);
  });
});

describe('useOfflineQueue conflict metadata', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
  });

  it('stores conflict metadata with queued operations', async () => {
    setupMockIndexedDB();

    const { useOfflineQueue } = await importFreshUseOfflineQueue();
    const { enqueue, getQueue, isReady } = useOfflineQueue();

    await vi.waitFor(() => expect(isReady.value).toBe(true));

    const operation = {
      type: 'UPDATE_MARKER',
      endpoint: '/api/v1/markers/1',
      method: 'PUT',
      data: { time: 999999 },
      timestamp: Date.now(),
      clientVersion: 'v1.0',
      attempts: 0,
    };

    await enqueue(operation);
    const queue = await getQueue();

    expect(queue[0].clientVersion).toBe('v1.0');
    expect(queue[0].attempts).toBe(0);
  });

  it('increments retry attempts on conflict', async () => {
    setupMockIndexedDB();

    const { useOfflineQueue } = await importFreshUseOfflineQueue();
    const { enqueue, updateQueueItem, getQueue, isReady } = useOfflineQueue();

    await vi.waitFor(() => expect(isReady.value).toBe(true));

    const operation = {
      type: 'UPDATE_MARKER',
      endpoint: '/api/v1/markers/1',
      method: 'PUT',
      data: { time: 123 },
      timestamp: Date.now(),
      attempts: 0,
    };

    const queueId = await enqueue(operation);
    
    // Simulate retry after conflict
    await updateQueueItem(queueId, { attempts: 1, lastError: 'Conflict detected' });
    
    const queue = await getQueue();
    const item = queue.find((q) => q.id === queueId);
    
    expect(item.attempts).toBe(1);
    expect(item.lastError).toBe('Conflict detected');
  });
});
