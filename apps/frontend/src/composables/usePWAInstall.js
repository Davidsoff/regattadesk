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
  return typeof globalThis.window !== 'undefined';
}

function detectPlatformValue() {
  if (!hasNavigator()) {
    return 'desktop';
  }

  const ua = navigator.userAgent || '';
  const platformHint = navigator.userAgentData?.platform || '';

  if (/iPhone|iPad|iPod/.test(ua) || /iPhone|iPad|iPod/.test(platformHint)) {
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

  globalThis.addEventListener('beforeinstallprompt', handleBeforeInstallPrompt);
  globalThis.addEventListener('appinstalled', handleAppInstalled);
  listenersRegistered = true;
}

async function showInstallPrompt(event) {
  if (!event) {
    return null;
  }

  try {
    await event.prompt();
    const choiceResult = await event.userChoice;
    return choiceResult.outcome;
  } catch (error) {
    console.error('Error showing install prompt:', error);
    return null;
  }
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
    if (
      hasWindow()
      && typeof globalThis.matchMedia === 'function'
      && globalThis.matchMedia('(display-mode: standalone)').matches
    ) {
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

  function cleanup() {
    if (hasWindow()) {
      globalThis.removeEventListener('beforeinstallprompt', handleBeforeInstallPrompt);
      globalThis.removeEventListener('appinstalled', handleAppInstalled);
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
    showInstallPrompt: () => showInstallPrompt(installPromptEvent),
    cleanup,
  };
}
