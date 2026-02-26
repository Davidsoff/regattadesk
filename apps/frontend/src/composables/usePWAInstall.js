/**
 * PWA installation composable
 * 
 * Manages PWA installation detection, prompts, and platform-specific behaviors.
 * Supports both automatic prompts (Chrome/Edge) and manual instructions (iOS Safari).
 */

import { ref, computed } from 'vue';

let installPromptEvent = null;
let isInitialized = false;

export function usePWAInstall() {
  const isInstallable = ref(false);
  
  // Detect platform immediately
  function detectPlatformValue() {
    const ua = navigator.userAgent || '';
    const p = navigator.platform || '';

    if (/iPhone|iPad|iPod/.test(ua) || /iPhone|iPad|iPod/.test(p)) {
      return 'ios';
    } else if (/Android/.test(ua)) {
      return 'android';
    } else {
      return 'desktop';
    }
  }
  
  const platform = ref(detectPlatformValue());

  // Detect if already installed
  const isInstalled = computed(() => {
    // Check for standalone mode
    if (window.matchMedia('(display-mode: standalone)').matches) {
      return true;
    }
    
    // Check iOS standalone mode
    if (navigator.standalone === true) {
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
    if (typeof window !== 'undefined') {
      window.removeEventListener('beforeinstallprompt', handleBeforeInstallPrompt);
      window.removeEventListener('appinstalled', handleAppInstalled);
    }
  }

  // Set up event listeners immediately (not in onMounted for SSR compatibility)
  if (typeof window !== 'undefined') {
    detectPlatform();
    
    // Listen for beforeinstallprompt event
    window.addEventListener('beforeinstallprompt', handleBeforeInstallPrompt);
    
    // Listen for appinstalled event
    window.addEventListener('appinstalled', handleAppInstalled);
  }

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
