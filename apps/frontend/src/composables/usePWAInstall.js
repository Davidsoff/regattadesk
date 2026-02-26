/**
 * PWA installation composable
 * 
 * Manages PWA installation detection, prompts, and platform-specific behaviors.
 * Supports both automatic prompts (Chrome/Edge) and manual instructions (iOS Safari).
 */

import { ref, computed } from 'vue';

let installPromptEvent = null;
let isInstallableState = null;
let platformState = null;
let listenersRegistered = false;

function hasNavigator() {
  return typeof navigator !== 'undefined';
}

function hasWindow() {
  return typeof window !== 'undefined';
}

function detectPlatformValue() {
  if (!hasNavigator()) {
    return 'desktop';
  }

  const ua = navigator.userAgent || '';
  const p = navigator.platform || '';

  if (/iPhone|iPad|iPod/.test(ua) || /iPhone|iPad|iPod/.test(p)) {
    return 'ios';
  }

  if (/Android/.test(ua)) {
    return 'android';
  }

  return 'desktop';
}

function setupListeners(handleBeforeInstallPrompt, handleAppInstalled) {
  if (!hasWindow() || listenersRegistered) {
    return;
  }

  window.addEventListener('beforeinstallprompt', handleBeforeInstallPrompt);
  window.addEventListener('appinstalled', handleAppInstalled);
  listenersRegistered = true;
}

export function usePWAInstall() {
  if (!isInstallableState) {
    isInstallableState = ref(false);
  }

  if (!platformState) {
    platformState = ref(detectPlatformValue());
  }

  const isInstallable = isInstallableState;
  const platform = platformState;

  // Detect if already installed
  const isInstalled = computed(() => {
    if (hasWindow() && typeof window.matchMedia === 'function' && window.matchMedia('(display-mode: standalone)').matches) {
      return true;
    }

    if (hasNavigator() && navigator.standalone === true) {
      return true;
    }

    return false;
  });

  const canInstall = computed(() => {
    return isInstallable.value && !isInstalled.value;
  });

  const needsManualInstructions = computed(() => {
    return platform.value === 'ios' && !isInstalled.value;
  });

  function detectPlatform() {
    platform.value = detectPlatformValue();
  }

  function handleBeforeInstallPrompt(event) {
    // Prevent the default install prompt
    event.preventDefault();
    
    // Store the event for later use
    installPromptEvent = event;
    isInstallable.value = true;
  }

  function handleAppInstalled() {
    // Clear the saved prompt since it's now installed
    installPromptEvent = null;
    isInstallable.value = false;
  }

  async function showInstallPrompt() {
    if (!installPromptEvent) {
      return null;
    }

    try {
      // Show the install prompt
      await installPromptEvent.prompt();
      
      // Wait for the user's response
      const choiceResult = await installPromptEvent.userChoice;
      
      return choiceResult.outcome;
    } catch (error) {
      console.error('Error showing install prompt:', error);
      return null;
    }
  }

  function cleanup() {
    if (hasWindow()) {
      window.removeEventListener('beforeinstallprompt', handleBeforeInstallPrompt);
      window.removeEventListener('appinstalled', handleAppInstalled);
      listenersRegistered = false;
    }
  }

  detectPlatform();
  setupListeners(handleBeforeInstallPrompt, handleAppInstalled);

  return {
    isInstallable,
    isInstalled,
    canInstall,
    platform,
    needsManualInstructions,
    showInstallPrompt,
    cleanup,
  };
}
