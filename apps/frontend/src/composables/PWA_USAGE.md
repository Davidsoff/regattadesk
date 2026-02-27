# PWA Offline Shell - Usage Guide

This directory contains composables for implementing PWA offline functionality with conflict resolution and accessibility features.

## Composables Overview

### 1. useServiceWorker
Manages service worker registration and lifecycle.

```javascript
import { useServiceWorker } from '@/composables/useServiceWorker';

const {
  isSupported,     // boolean - SW supported in browser
  isRegistered,    // boolean - SW currently registered
  registration,    // ServiceWorkerRegistration object
  state,          // string - SW state (installing|waiting|activated)
  error,          // Error object if registration failed
  register,       // async function - register SW
  update,         // async function - check for updates
  sendMessage,    // function - send message to SW
  onMessage,      // function - listen for SW messages
} = useServiceWorker();

// Register service worker
await register('/sw.js');

// Send message to service worker
sendMessage({ type: 'SYNC_QUEUE' });

// Listen for messages from service worker
onMessage((message) => {
  console.log('Received:', message);
});
```

### 2. usePWAInstall
Detects PWA installation capability and manages install prompts.

```javascript
import { usePWAInstall } from '@/composables/usePWAInstall';

const {
  isInstallable,            // boolean - can show install prompt
  isInstalled,              // boolean - already installed
  canInstall,               // computed - installable && !installed
  platform,                 // string - ios|android|desktop
  needsManualInstructions,  // boolean - iOS requires manual steps
  showInstallPrompt,        // async function - show native prompt
} = usePWAInstall();

// Show install prompt
if (canInstall.value) {
  const outcome = await showInstallPrompt(); // 'accepted' | 'dismissed' | null
}
```

### 3. useOperatorTheme
Manages operator-specific theme with high-contrast defaults.

```javascript
import { useOperatorTheme } from '@/composables/useOperatorTheme';

const {
  contrast,          // ref('high'|'standard') - current contrast mode
  density,           // ref('comfortable'|'compact'|'spacious')
  isHighContrast,    // computed boolean
  setContrast,       // function - set contrast mode
  setDensity,        // function - set density mode
  toggleContrast,    // function - toggle between modes
} = useOperatorTheme();

// Default is high-contrast for operators
console.log(contrast.value); // 'high'

// Toggle contrast
toggleContrast();

// Set density
setDensity('compact');
```

### 4. useOfflineQueue
Manages local IndexedDB queue for offline operations.

```javascript
import { useOfflineQueue } from '@/composables/useOfflineQueue';

const {
  isReady,         // boolean - IndexedDB initialized
  error,           // Error if initialization failed
  queueSize,       // number - current queue length
  enqueue,         // async function - add operation to queue
  dequeue,         // async function - remove operation from queue
  getQueue,        // async function - get all queued operations
  clearQueue,      // async function - clear all operations
  updateQueueItem, // async function - update operation metadata
} = useOfflineQueue();

// Wait for initialization
await vi.waitFor(() => isReady.value === true);

// Enqueue an operation
const queueId = await enqueue({
  type: 'CREATE_MARKER',
  endpoint: '/api/v1/markers',
  method: 'POST',
  data: { time: 123456, bib: '42' },
  timestamp: Date.now(),
  conflictStrategy: 'last-write-wins', // or 'manual', 'client-wins', 'server-wins'
});

// Get all queued operations
const queue = await getQueue();

// Remove operation after successful sync
await dequeue(queueId);
```

### 5. useOfflineSync
Syncs queued operations with configurable conflict resolution.

```javascript
import { useOfflineSync } from '@/composables/useOfflineSync';
import { useOfflineQueue } from '@/composables/useOfflineQueue';

const { syncQueue, isSyncing } = useOfflineSync();
const { getQueue, dequeue } = useOfflineQueue();

// Sync all queued operations
const queue = await getQueue();
const result = await syncQueue(queue, { enableRetry: true });

// Handle results
result.synced.forEach(async (item) => {
  // Remove successfully synced operations
  await dequeue(item.id);
});

result.conflicts.forEach((conflict) => {
  if (conflict.requiresManualResolution) {
    // Show UI for manual conflict resolution
    console.log('Conflict:', conflict.clientData, 'vs', conflict.serverData);
  }
});

result.failed.forEach((failure) => {
  console.error('Failed to sync:', failure.error);
});

result.discarded.forEach((item) => {
  // Server won, discard client changes
  await dequeue(item.id);
});
```

## Conflict Resolution Strategies

When syncing offline operations, conflicts can occur if server data changed while offline. Configure strategy per operation:

- **`last-write-wins`** (default): Compare timestamps, newer wins
- **`client-wins`**: Force client data to server (use with caution)
- **`server-wins`**: Discard client changes, accept server state
- **`manual`**: Queue for manual resolution in UI

Example:
```javascript
await enqueue({
  type: 'UPDATE_MARKER',
  endpoint: `/api/v1/markers/${markerId}`,
  method: 'PUT',
  data: { time: newTime },
  timestamp: Date.now(),
  conflictStrategy: 'last-write-wins',
  clientVersion: 'v1.0', // Optional version tracking
});
```

## Integration Example

Typical operator PWA setup:

```javascript
// In main app setup
import { useServiceWorker } from '@/composables/useServiceWorker';
import { useOperatorTheme } from '@/composables/useOperatorTheme';
import { usePWAInstall } from '@/composables/usePWAInstall';

export default {
  setup() {
    const { register, onMessage } = useServiceWorker();
    const { isHighContrast } = useOperatorTheme();
    const { canInstall, showInstallPrompt } = usePWAInstall();

    // Register service worker on mount
    onMounted(async () => {
      try {
        await register('/sw.js');
        console.log('Service worker registered');
      } catch (error) {
        console.error('SW registration failed:', error);
      }
    });

    // Listen for sync complete messages
    onMessage((message) => {
      if (message.type === 'SYNC_COMPLETE') {
        console.log('Background sync completed');
      }
    });

    return {
      isHighContrast,
      canInstall,
      showInstallPrompt,
    };
  },
};
```

## Browser Support

- **Service Workers**: Chrome/Firefox/Safari (modern versions)
- **IndexedDB**: All modern browsers
- **Install Prompts**: Chrome/Edge (automatic), Safari iOS (manual instructions)
- **Background Sync**: Chrome/Edge (optional enhancement)

## Testing

All composables include comprehensive test suites. Run tests with:

```bash
npm test
```

See `src/__tests__/` for test examples showing usage patterns.
