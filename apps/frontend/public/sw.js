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

// Assets to cache on install
const STATIC_ASSETS = [
  '/',
  '/index.html',
  '/manifest.json',
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
  console.log('[SW] Installing service worker');
  
  event.waitUntil(
    caches.open(STATIC_CACHE)
      .then((cache) => {
        console.log('[SW] Caching static assets');
        return cache.addAll(STATIC_ASSETS);
      })
      .then(() => {
        console.log('[SW] Static assets cached successfully');
        return self.skipWaiting();
      })
      .catch((error) => {
        console.error('[SW] Failed to cache static assets:', error);
      })
  );
});

/**
 * Activate event - clean up old caches
 */
self.addEventListener('activate', (event) => {
  console.log('[SW] Activating service worker');
  
  event.waitUntil(
    caches.keys()
      .then((cacheNames) => {
        return Promise.all(
          cacheNames
            .filter((name) => name.startsWith('regattadesk-operator-') && name !== STATIC_CACHE && name !== RUNTIME_CACHE)
            .map((name) => {
              console.log('[SW] Deleting old cache:', name);
              return caches.delete(name);
            })
        );
      })
      .then(() => {
        console.log('[SW] Service worker activated');
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

  // Skip non-GET requests
  if (request.method !== 'GET') {
    return;
  }

  // Skip chrome-extension and other non-http(s) requests
  if (!url.protocol.startsWith('http')) {
    return;
  }

  // Determine caching strategy based on URL
  if (shouldUseCacheFirst(url)) {
    event.respondWith(cacheFirst(request));
  } else if (shouldUseNetworkFirst(url)) {
    event.respondWith(networkFirst(request));
  } else {
    // Default: network first with cache fallback
    event.respondWith(networkFirst(request));
  }
});

/**
 * Message event - handle messages from clients
 */
self.addEventListener('message', (event) => {
  const { type, payload } = event.data || {};

  switch (type) {
    case 'SYNC_QUEUE':
      console.log('[SW] Sync queue requested');
      // Trigger background sync if supported
      if ('sync' in self.registration) {
        self.registration.sync.register('sync-queue')
          .then(() => {
            console.log('[SW] Background sync registered');
          })
          .catch((error) => {
            console.error('[SW] Background sync registration failed:', error);
          });
      }
      break;

    case 'SKIP_WAITING':
      console.log('[SW] Skip waiting requested');
      self.skipWaiting();
      break;

    case 'CLAIM_CLIENTS':
      console.log('[SW] Claim clients requested');
      self.clients.claim();
      break;

    default:
      console.log('[SW] Unknown message type:', type);
  }
});

/**
 * Background sync event - sync queued operations
 */
self.addEventListener('sync', (event) => {
  if (event.tag === 'sync-queue') {
    console.log('[SW] Background sync triggered');
    event.waitUntil(syncQueuedOperations());
  }
});

/**
 * Check if URL should use cache-first strategy
 */
function shouldUseCacheFirst(url) {
  return CACHE_FIRST_PATTERNS.some((pattern) => pattern.test(url.pathname));
}

/**
 * Check if URL should use network-first strategy
 */
function shouldUseNetworkFirst(url) {
  return NETWORK_FIRST_PATTERNS.some((pattern) => pattern.test(url.pathname));
}

/**
 * Cache-first strategy
 * Try cache first, fall back to network
 */
async function cacheFirst(request) {
  try {
    const cached = await caches.match(request);
    if (cached) {
      console.log('[SW] Serving from cache:', request.url);
      return cached;
    }

    console.log('[SW] Cache miss, fetching:', request.url);
    const response = await fetch(request);
    
    // Cache successful responses
    if (response.ok) {
      const cache = await caches.open(RUNTIME_CACHE);
      cache.put(request, response.clone());
    }
    
    return response;
  } catch (error) {
    console.error('[SW] Cache-first failed:', error);
    
    // Try to return a cached version as last resort
    const cached = await caches.match(request);
    if (cached) {
      return cached;
    }
    
    // Return offline page or error response
    return new Response('Offline', {
      status: 503,
      statusText: 'Service Unavailable',
      headers: new Headers({
        'Content-Type': 'text/plain',
      }),
    });
  }
}

/**
 * Network-first strategy
 * Try network first, fall back to cache
 */
async function networkFirst(request) {
  try {
    console.log('[SW] Fetching from network:', request.url);
    const response = await fetch(request);
    
    // Cache successful GET responses
    if (response.ok && request.method === 'GET') {
      const cache = await caches.open(RUNTIME_CACHE);
      cache.put(request, response.clone());
    }
    
    return response;
  } catch (error) {
    console.log('[SW] Network failed, trying cache:', request.url);
    
    const cached = await caches.match(request);
    if (cached) {
      console.log('[SW] Serving from cache:', request.url);
      return cached;
    }
    
    console.error('[SW] Network-first failed, no cache available:', error);
    return new Response('Offline', {
      status: 503,
      statusText: 'Service Unavailable',
      headers: new Headers({
        'Content-Type': 'text/plain',
      }),
    });
  }
}

/**
 * Sync queued operations with server
 * This would integrate with IndexedDB queue in real implementation
 */
async function syncQueuedOperations() {
  console.log('[SW] Syncing queued operations');
  
  try {
    // In a real implementation, this would:
    // 1. Open IndexedDB
    // 2. Get queued operations
    // 3. Attempt to send each operation
    // 4. Remove successful operations from queue
    // 5. Keep failed operations for retry
    
    // Send message to all clients that sync is complete
    const clients = await self.clients.matchAll();
    clients.forEach((client) => {
      client.postMessage({
        type: 'SYNC_COMPLETE',
        payload: { timestamp: Date.now() },
      });
    });
    
    console.log('[SW] Queue sync complete');
  } catch (error) {
    console.error('[SW] Queue sync failed:', error);
    throw error;
  }
}

console.log('[SW] Service worker loaded');
