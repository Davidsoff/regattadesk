/**
 * Service Worker registration and management composable
 *
 * Handles service worker lifecycle, state tracking, and messaging.
 * Provides reactive state for service worker registration and communication.
 *
 * Singleton pattern: module-level variables are shared across all component
 * instances that call useServiceWorker(). This is intentional — there is only
 * one service worker per origin, so all components should observe the same
 * registration and lifecycle state. The pattern avoids duplicate registrations
 * and ensures a single source of truth for SW state.
 */

import { ref, computed } from 'vue';

// Module-level singleton refs (see file-level comment above)
let _swRegistration = null; // ref<ServiceWorkerRegistration|null>
let _swIsRegistered = null; // ref<boolean>
let _swLifecycleState = null; // ref<string> — 'idle' | 'installing' | 'installed' | 'activating' | 'activated' | 'redundant'
let _swError = null; // ref<Error|null>
let messageListeners = [];

function createMessageEventHandler(callback) {
  return (event) => {
    callback(event.data);
  };
}

function onMessage(callback) {
  const handler = createMessageEventHandler(callback);

  if (navigator.serviceWorker) {
    navigator.serviceWorker.addEventListener('message', handler);
    messageListeners.push({ callback, handler });
  }

  // Return cleanup function
  return () => {
    if (navigator.serviceWorker) {
      navigator.serviceWorker.removeEventListener('message', handler);
      messageListeners = messageListeners.filter((listener) => listener.callback !== callback);
    }
  };
}

export function useServiceWorker() {
  // Initialize singleton state on first call
  if (!_swRegistration) {
    _swRegistration = ref(null);
    _swIsRegistered = ref(false);
    _swLifecycleState = ref('idle');
    _swError = ref(null);
  }

  // Expose refs under clean public names
  const registration = _swRegistration;
  const isRegistered = _swIsRegistered;
  const state = _swLifecycleState;
  const error = _swError;

  const isSupported = computed(() => {
    return typeof navigator !== 'undefined' && 'serviceWorker' in navigator;
  });

  function updateServiceWorkerState(sw) {
    if (!sw) return;
    
    state.value = sw.state;
    
    if (sw.addEventListener && typeof sw.addEventListener === 'function') {
      sw.addEventListener('statechange', () => {
        state.value = sw.state;
      });
    }
  }

  async function register(scriptURL, options = {}) {
    if (!isSupported.value) {
      console.warn('Service Worker not supported in this browser');
      return;
    }

    // Don't re-register if already registered
    if (isRegistered.value && registration.value) {
      return registration.value;
    }

    try {
      const reg = await navigator.serviceWorker.register(scriptURL, {
        scope: '/',
        ...options,
      });

      registration.value = reg;
      isRegistered.value = true;
      error.value = null;

      // Track the installing, waiting, or active service worker
      if (reg.installing) {
        updateServiceWorkerState(reg.installing);
      } else if (reg.waiting) {
        updateServiceWorkerState(reg.waiting);
      } else if (reg.active) {
        updateServiceWorkerState(reg.active);
      }

      // Listen for updates
      reg.addEventListener('updatefound', () => {
        const newWorker = reg.installing;
        if (newWorker) {
          updateServiceWorkerState(newWorker);
        }
      });

      return reg;
    } catch (err) {
      console.error('Service Worker registration failed:', err);
      error.value = err;
      isRegistered.value = false;
      throw err;
    }
  }

  async function update() {
    if (!registration.value) {
      console.warn('No service worker registered to update');
      return;
    }

    try {
      await registration.value.update();
    } catch (err) {
      console.error('Service Worker update failed:', err);
      error.value = err;
    }
  }

  async function unregister() {
    if (!registration.value) {
      return;
    }

    try {
      const success = await registration.value.unregister();
      if (success) {
        registration.value = null;
        isRegistered.value = false;
        state.value = 'idle';
      }
      return success;
    } catch (err) {
      console.error('Service Worker unregistration failed:', err);
      error.value = err;
      return false;
    }
  }

  function sendMessage(message) {
    if (!registration.value?.active) {
      console.warn('No active service worker to send message to');
      return;
    }

    registration.value.active.postMessage(message);
  }

  function cleanup() {
    // Clean up message listeners
    messageListeners.forEach(({ handler }) => {
      if (navigator.serviceWorker) {
        navigator.serviceWorker.removeEventListener('message', handler);
      }
    });
    messageListeners = [];
    _swRegistration = null;
    _swIsRegistered = null;
    _swLifecycleState = null;
    _swError = null;
  }

  return {
    isSupported,
    isRegistered,
    registration,
    state,
    error,
    register,
    update,
    unregister,
    sendMessage,
    onMessage,
    cleanup,
  };
}
