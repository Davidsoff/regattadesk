/**
 * Offline queue management composable using IndexedDB
 * 
 * Provides persistent storage for offline operations with FIFO ordering.
 * Supports conflict metadata tracking for sync protocol.
 */

import { ref } from 'vue';

const DB_NAME = 'regattadesk-operator';
const DB_VERSION = 1;
const STORE_NAME = 'offline-queue';

let dbInstance = null;
let isReadyState = null;
let errorState = null;
let queueSizeState = null;

function isIndexedDbAvailable() {
  return typeof indexedDB !== 'undefined';
}

function sanitizeOperation(operation) {
  if (!operation || typeof operation !== 'object' || Array.isArray(operation)) {
    throw new Error('Invalid operation payload');
  }

  const allowedMethods = new Set(['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS']);
  const method = typeof operation.method === 'string' ? operation.method.toUpperCase() : '';
  if (!allowedMethods.has(method)) {
    throw new Error('Invalid operation method');
  }

  if (typeof operation.endpoint !== 'string' || operation.endpoint.length === 0) {
    throw new Error('Invalid operation endpoint');
  }

  return {
    endpoint: operation.endpoint,
    method,
    data: operation.data ?? null,
    timestamp: Number.isFinite(operation.timestamp) ? operation.timestamp : Date.now(),
    clientVersion: operation.clientVersion,
    maxRetries: operation.maxRetries,
    conflictStrategy: operation.conflictStrategy,
  };
}

function openDatabase() {
  return new Promise((resolve, reject) => {
    if (!isIndexedDbAvailable()) {
      reject(new Error('IndexedDB is not available in this environment'));
      return;
    }

    const request = indexedDB.open(DB_NAME, DB_VERSION);

    request.onerror = () => {
      reject(request.error);
    };

    request.onsuccess = () => {
      resolve(request.result);
    };

    request.onupgradeneeded = (event) => {
      const db = event.target.result;
      
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        const store = db.createObjectStore(STORE_NAME, {
          keyPath: 'id',
          autoIncrement: true,
        });
        
        // Index for timestamp-based queries
        store.createIndex('timestamp', 'timestamp', { unique: false });
      }
    };
  });
}

async function ensureDatabase() {
  if (dbInstance) {
    return dbInstance;
  }

  try {
    dbInstance = await openDatabase();
    return dbInstance;
  } catch (err) {
    console.error('Failed to open IndexedDB:', err);
    throw err;
  }
}

function requestToPromise(request) {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => {
      resolve(request.result);
    };

    request.onerror = () => {
      reject(request.error);
    };
  });
}

async function getQueueInternal() {
  const db = await ensureDatabase();
  const transaction = db.transaction([STORE_NAME], 'readonly');
  const store = transaction.objectStore(STORE_NAME);
  const result = await requestToPromise(store.getAll());
  return result || [];
}

async function updateQueueItemInternal(queueId, updates) {
  const db = await ensureDatabase();
  const transaction = db.transaction([STORE_NAME], 'readwrite');
  const store = transaction.objectStore(STORE_NAME);

  const item = await requestToPromise(store.get(queueId));
  if (!item) {
    throw new Error('Queue item not found');
  }

  const updatedItem = { ...item, ...updates };
  await requestToPromise(store.put(updatedItem));
  return updatedItem;
}

export function useOfflineQueue() {
  // Initialize singleton state
  if (!isReadyState) {
    isReadyState = ref(false);
    errorState = ref(null);
    queueSizeState = ref(0);

    // Initialize database
    ensureDatabase()
      .then(async () => {
        isReadyState.value = true;
        // Update queue size
        const queue = await getQueueInternal();
        queueSizeState.value = queue.length;
      })
      .catch((err) => {
        errorState.value = err;
        console.error('Database initialization failed:', err);
      });
  }

  const isReady = isReadyState;
  const error = errorState;
  const queueSize = queueSizeState;

  async function refreshQueueSize() {
    const queue = await getQueueInternal();
    queueSizeState.value = queue.length;
  }

  async function enqueue(operation) {
    const db = await ensureDatabase();
    const item = sanitizeOperation(operation);
    
    return new Promise((resolve, reject) => {
      const transaction = db.transaction([STORE_NAME], 'readwrite');
      const store = transaction.objectStore(STORE_NAME);

      const request = store.add(item);

      request.onsuccess = async () => {
        try {
          await refreshQueueSize();
          resolve(request.result);
        } catch (err) {
          reject(err);
        }
      };

      request.onerror = () => {
        reject(request.error);
      };
    });
  }

  async function dequeue(queueId) {
    const db = await ensureDatabase();
    
    return new Promise((resolve, reject) => {
      const transaction = db.transaction([STORE_NAME], 'readwrite');
      const store = transaction.objectStore(STORE_NAME);
      const request = store.delete(queueId);

      request.onsuccess = async () => {
        try {
          await refreshQueueSize();
          resolve();
        } catch (err) {
          reject(err);
        }
      };

      request.onerror = () => {
        reject(request.error);
      };
    });
  }

  async function getQueue() {
    return getQueueInternal();
  }

  async function clearQueue() {
    const db = await ensureDatabase();
    
    return new Promise((resolve, reject) => {
      const transaction = db.transaction([STORE_NAME], 'readwrite');
      const store = transaction.objectStore(STORE_NAME);
      const request = store.clear();

      request.onsuccess = async () => {
        try {
          await refreshQueueSize();
          resolve();
        } catch (err) {
          reject(err);
        }
      };

      request.onerror = () => {
        reject(request.error);
      };
    });
  }

  async function updateQueueItem(queueId, updates) {
    return updateQueueItemInternal(queueId, updates);
  }

  return {
    isReady,
    error,
    queueSize,
    enqueue,
    dequeue,
    getQueue,
    clearQueue,
    updateQueueItem,
  };
}

export function closeOfflineQueueDatabase() {
  if (dbInstance) {
    dbInstance.close();
    dbInstance = null;
  }
}
