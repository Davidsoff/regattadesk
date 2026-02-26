/**
 * Offline queue management composable using IndexedDB
 * 
 * Provides persistent storage for offline operations with FIFO ordering.
 * Supports conflict metadata tracking for sync protocol.
 */

import { ref, computed } from 'vue';

const DB_NAME = 'regattadesk-operator';
const DB_VERSION = 1;
const STORE_NAME = 'offline-queue';

let dbInstance = null;
let isReadyState = null;
let errorState = null;
let queueSizeState = null;

function openDatabase() {
  return new Promise((resolve, reject) => {
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

  async function getQueueInternal() {
    const db = await ensureDatabase();
    
    return new Promise((resolve, reject) => {
      const transaction = db.transaction([STORE_NAME], 'readonly');
      const store = transaction.objectStore(STORE_NAME);
      const request = store.getAll();

      request.onsuccess = () => {
        resolve(request.result || []);
      };

      request.onerror = () => {
        reject(request.error);
      };
    });
  }

  async function enqueue(operation) {
    const db = await ensureDatabase();
    
    return new Promise((resolve, reject) => {
      const transaction = db.transaction([STORE_NAME], 'readwrite');
      const store = transaction.objectStore(STORE_NAME);
      
      const item = {
        ...operation,
        timestamp: operation.timestamp || Date.now(),
      };
      
      const request = store.add(item);

      request.onsuccess = () => {
        queueSizeState.value++;
        resolve(request.result);
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

      request.onsuccess = () => {
        queueSizeState.value = Math.max(0, queueSizeState.value - 1);
        resolve();
      };

      request.onerror = () => {
        reject(request.error);
      };
    });
  }

  async function getQueue() {
    return await getQueueInternal();
  }

  async function clearQueue() {
    const db = await ensureDatabase();
    
    return new Promise((resolve, reject) => {
      const transaction = db.transaction([STORE_NAME], 'readwrite');
      const store = transaction.objectStore(STORE_NAME);
      const request = store.clear();

      request.onsuccess = () => {
        queueSizeState.value = 0;
        resolve();
      };

      request.onerror = () => {
        reject(request.error);
      };
    });
  }

  async function updateQueueItem(queueId, updates) {
    const db = await ensureDatabase();
    
    return new Promise((resolve, reject) => {
      const transaction = db.transaction([STORE_NAME], 'readwrite');
      const store = transaction.objectStore(STORE_NAME);
      
      // Get the existing item
      const getRequest = store.get(queueId);
      
      getRequest.onsuccess = () => {
        const item = getRequest.result;
        if (!item) {
          reject(new Error('Queue item not found'));
          return;
        }
        
        // Merge updates
        const updatedItem = { ...item, ...updates };
        
        // Put the updated item back
        const putRequest = store.put(updatedItem);
        
        putRequest.onsuccess = () => {
          resolve(updatedItem);
        };
        
        putRequest.onerror = () => {
          reject(putRequest.error);
        };
      };
      
      getRequest.onerror = () => {
        reject(getRequest.error);
      };
    });
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
