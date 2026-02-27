/**
 * Service Worker registration and management composable
 * 
 * Handles service worker lifecycle, state tracking, and messaging.
 * Provides reactive state for service worker registration and communication.
 */

import { ref, computed } from 'vue';

let registrationState = null;
let isRegisteredState = null;
let stateRef = null;
let errorState = null;
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
  // Initialize singleton state
  if (!registrationState) {
    registrationState = ref(null);
    isRegisteredState = ref(false);
    stateRef = ref('idle');
    errorState = ref(null);
  }

  const registration = registrationState;
  const isRegistered = isRegisteredState;
  const state = stateRef;
  const error = errorState;

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
    registrationState = null;
    isRegisteredState = null;
    stateRef = null;
    errorState = null;
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
