/**
 * RegattaDesk Operator PWA Service Worker
 *
 * Provides offline capability for operator stations with:
 * - Asset caching for offline-first operation
 * - Background sync for queued operations
 * - Cache-first strategy for static assets
 * - Network-first strategy for API calls with offline queue
 */

const CACHE_VERSION = 'regattadesk-operator-v1';
const STATIC_CACHE = `${CACHE_VERSION}-static`;
const RUNTIME_CACHE = `${CACHE_VERSION}-runtime`;
const DB_NAME = 'regattadesk-operator';
const DB_VERSION = 1;
const STORE_NAME = 'offline-queue';
const OFFLINE_PAGE = '/offline.html';
const DEBUG_LOGS = self.location.hostname === 'localhost' || self.location.hostname === '127.0.0.1';

function log(...args) {
  if (DEBUG_LOGS) {
    console.log(...args);
  }
}

// Assets to cache on install
const STATIC_ASSETS = [
  '/',
  '/index.html',
  '/manifest.json',
  OFFLINE_PAGE,
  '/icons/icon-192x192.png',
  '/icons/icon-512x512.png',
];

// Cache-first resources (static assets)
const CACHE_FIRST_PATTERNS = [
  /\.(?:js|css|png|jpg|jpeg|svg|gif|woff2?|ttf|eot)$/,
  /^\/assets\//,
];

// Network-first resources (API calls)
const NETWORK_FIRST_PATTERNS = [
  /^\/api\//,
];

/**
 * Install event - cache static assets
 */
self.addEventListener('install', (event) => {
  log('[SW] Installing service worker');

  event.waitUntil((async () => {
    try {
      const cache = await caches.open(STATIC_CACHE);
      log('[SW] Caching static assets');
      await cache.addAll(STATIC_ASSETS);
      log('[SW] Static assets cached successfully');
      await self.skipWaiting();
    } catch (error) {
      console.error('[SW] Failed to cache static assets:', error);
      throw error;
    }
  })());
});

/**
 * Activate event - clean up old caches
 */
self.addEventListener('activate', (event) => {
  log('[SW] Activating service worker');

  event.waitUntil(
    caches.keys()
      .then((cacheNames) => {
        return Promise.all(
          cacheNames
            .filter((name) => name.startsWith('regattadesk-operator-') && name !== STATIC_CACHE && name !== RUNTIME_CACHE)
            .map((name) => {
              log('[SW] Deleting old cache:', name);
              return caches.delete(name);
            })
        );
      })
      .then(() => {
        log('[SW] Service worker activated');
        return self.clients.claim();
      })
  );
});

/**
 * Fetch event - handle requests with appropriate strategy
 */
self.addEventListener('fetch', (event) => {
  const { request } = event;
  const url = new URL(request.url);

  if (request.method !== 'GET') {
    return;
  }

  if (!url.protocol.startsWith('http')) {
    return;
  }

  if (url.origin !== self.location.origin) {
    return;
  }

  if (shouldUseCacheFirst(url)) {
    event.respondWith(cacheFirst(request));
  } else if (shouldUseNetworkFirst(url)) {
    event.respondWith(networkFirst(request));
  } else {
    event.respondWith(networkFirst(request));
  }
});

/**
 * Message event - handle messages from clients
 */
self.addEventListener('message', (event) => {
  const { type } = event.data || {};

  switch (type) {
    case 'SYNC_QUEUE':
      log('[SW] Sync queue requested');
      if ('sync' in self.registration) {
        self.registration.sync.register('sync-queue')
          .then(() => {
            log('[SW] Background sync registered');
          })
          .catch((error) => {
            console.error('[SW] Background sync registration failed:', error);
          });
      }
      break;

    case 'SKIP_WAITING':
      log('[SW] Skip waiting requested');
      self.skipWaiting();
      break;

    case 'CLAIM_CLIENTS':
      log('[SW] Claim clients requested');
      self.clients.claim();
      break;

    default:
      log('[SW] Unknown message type:', type);
  }
});

/**
 * Background sync event - sync queued operations
 */
self.addEventListener('sync', (event) => {
  if (event.tag === 'sync-queue') {
    log('[SW] Background sync triggered');
    event.waitUntil(syncQueuedOperations());
  }
});

function shouldUseCacheFirst(url) {
  return CACHE_FIRST_PATTERNS.some((pattern) => pattern.test(url.pathname));
}

function shouldUseNetworkFirst(url) {
  return NETWORK_FIRST_PATTERNS.some((pattern) => pattern.test(url.pathname));
}

async function cacheFirst(request) {
  try {
    const cached = await caches.match(request);
    if (cached) {
      log('[SW] Serving from cache:', request.url);
      return cached;
    }

    log('[SW] Cache miss, fetching:', request.url);
    const response = await fetch(request);

    if (response.ok) {
      const cache = await caches.open(RUNTIME_CACHE);
      await cache.put(request, response.clone());
    }

    return response;
  } catch (error) {
    console.error('[SW] Cache-first failed:', error);

    const cached = await caches.match(request);
    if (cached) {
      return cached;
    }

    return offlineResponseFor(request);
  }
}

async function networkFirst(request) {
  try {
    log('[SW] Fetching from network:', request.url);
    const response = await fetch(request);

    if (response.ok && request.method === 'GET') {
      const cache = await caches.open(RUNTIME_CACHE);
      await cache.put(request, response.clone());
    }

    return response;
  } catch (error) {
    log('[SW] Network failed, trying cache:', request.url);

    const cached = await caches.match(request);
    if (cached) {
      log('[SW] Serving from cache:', request.url);
      return cached;
    }

    console.error('[SW] Network-first failed, no cache available:', error);
    return offlineResponseFor(request);
  }
}

async function offlineResponseFor(request) {
  const isNavigation = request.mode === 'navigate'
    || request.destination === 'document'
    || request.headers.get('accept')?.includes('text/html');

  if (isNavigation) {
    const offlinePage = await caches.match(OFFLINE_PAGE);
    if (offlinePage) {
      return offlinePage;
    }
  }

  return new Response('Offline', {
    status: 503,
    statusText: 'Service Unavailable',
    headers: new Headers({
      'Content-Type': 'text/plain',
    }),
  });
}

function openQueueDatabase() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);

    request.onerror = () => reject(request.error);
    request.onsuccess = () => resolve(request.result);
  });
}

function getQueuedOperations(db) {
  return new Promise((resolve, reject) => {
    const transaction = db.transaction([STORE_NAME], 'readonly');
    const store = transaction.objectStore(STORE_NAME);
    const request = store.getAll();

    request.onsuccess = () => {
      const operations = (request.result || []).slice().sort((a, b) => (a.timestamp || 0) - (b.timestamp || 0));
      resolve(operations);
    };
    request.onerror = () => reject(request.error);
  });
}

function deleteQueuedOperation(db, id) {
  return new Promise((resolve, reject) => {
    const transaction = db.transaction([STORE_NAME], 'readwrite');
    const store = transaction.objectStore(STORE_NAME);
    const request = store.delete(id);

    request.onsuccess = () => resolve();
    request.onerror = () => reject(request.error);
  });
}

function buildSyncRequest(operation) {
  const method = typeof operation.method === 'string' ? operation.method.toUpperCase() : 'GET';
  const endpoint = typeof operation.endpoint === 'string' ? operation.endpoint : '';

  if (!endpoint.startsWith('/api/')) {
    throw new Error('Skipping invalid queued endpoint');
  }

  const request = {
    method,
    headers: {
      'Content-Type': 'application/json',
    },
  };

  if (['POST', 'PUT', 'PATCH'].includes(method)) {
    request.body = JSON.stringify(operation.data ?? null);
  }

  return {
    endpoint,
    request,
  };
}

async function syncQueuedOperations() {
  log('[SW] Syncing queued operations');

  let synced = 0;
  let failed = 0;

  try {
    const db = await openQueueDatabase();

    try {
      const operations = await getQueuedOperations(db);

      for (const operation of operations) {
        try {
          const { endpoint, request } = buildSyncRequest(operation);
          const response = await fetch(endpoint, request);

          if (response.ok) {
            await deleteQueuedOperation(db, operation.id);
            synced += 1;
          } else {
            failed += 1;
          }
        } catch (error) {
          failed += 1;
          console.error('[SW] Failed syncing queued operation:', error);
        }
      }
    } finally {
      db.close();
    }

    const clients = await self.clients.matchAll();
    clients.forEach((client) => {
      client.postMessage({
        type: 'SYNC_COMPLETE',
        payload: {
          timestamp: Date.now(),
          synced,
          failed,
        },
      });
    });

    log('[SW] Queue sync complete', { synced, failed });
  } catch (error) {
    console.error('[SW] Queue sync failed:', error);
    throw error;
  }
}

log('[SW] Service worker loaded');
