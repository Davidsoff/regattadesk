/**
 * Operator theme management composable
 * 
 * Manages high-contrast mode and density settings for operator PWA.
 * Defaults to high-contrast mode with per-device persistence.
 */

import { ref, computed } from 'vue';

const STORAGE_KEY_CONTRAST = 'regattadesk-operator-contrast';
const STORAGE_KEY_DENSITY = 'regattadesk-operator-density';
const VALID_CONTRAST_VALUES = ['high', 'standard'];
const VALID_DENSITY_VALUES = ['comfortable', 'compact', 'spacious'];

// Singleton state to share across component instances
let contrastState = null;
let densityState = null;

function canUseLocalStorage() {
  return typeof localStorage !== 'undefined';
}

function canUseDocument() {
  return typeof document !== 'undefined';
}

function initializeContrast() {
  if (contrastState) return contrastState;

  let initial = 'high'; // Default to high-contrast for operators

  try {
    if (canUseLocalStorage()) {
      const stored = localStorage.getItem(STORAGE_KEY_CONTRAST);
      if (stored && VALID_CONTRAST_VALUES.includes(stored)) {
        initial = stored;
      }
    }
  } catch (error) {
    console.warn('Failed to read operator contrast from localStorage:', error);
  }

  contrastState = ref(initial);
  
  if (canUseDocument()) {
    document.documentElement.dataset.contrast = initial;
  }

  return contrastState;
}

function initializeDensity() {
  if (densityState) return densityState;

  let initial = 'comfortable'; // Default density

  try {
    if (canUseLocalStorage()) {
      const stored = localStorage.getItem(STORAGE_KEY_DENSITY);
      if (stored && VALID_DENSITY_VALUES.includes(stored)) {
        initial = stored;
      }
    }
  } catch (error) {
    console.warn('Failed to read operator density from localStorage:', error);
  }

  densityState = ref(initial);
  
  if (canUseDocument()) {
    document.documentElement.dataset.density = initial;
  }

  return densityState;
}

export function useOperatorTheme() {
  const contrast = initializeContrast();
  const density = initializeDensity();

  const isHighContrast = computed(() => contrast.value === 'high');

  function setContrast(value) {
    if (!VALID_CONTRAST_VALUES.includes(value)) {
      console.warn(`Invalid contrast value: ${value}. Must be one of: ${VALID_CONTRAST_VALUES.join(', ')}`);
      return;
    }

    contrast.value = value;
    if (canUseDocument()) {
      document.documentElement.dataset.contrast = value;
    }

    try {
      if (canUseLocalStorage()) {
        localStorage.setItem(STORAGE_KEY_CONTRAST, value);
      }
    } catch (error) {
      console.warn('Failed to persist operator contrast to localStorage:', error);
    }
  }

  function setDensity(value) {
    if (!VALID_DENSITY_VALUES.includes(value)) {
      console.warn(`Invalid density value: ${value}. Must be one of: ${VALID_DENSITY_VALUES.join(', ')}`);
      return;
    }

    density.value = value;
    if (canUseDocument()) {
      document.documentElement.dataset.density = value;
    }

    try {
      if (canUseLocalStorage()) {
        localStorage.setItem(STORAGE_KEY_DENSITY, value);
      }
    } catch (error) {
      console.warn('Failed to persist operator density to localStorage:', error);
    }
  }

  function toggleContrast() {
    const newValue = contrast.value === 'high' ? 'standard' : 'high';
    setContrast(newValue);
  }

  return {
    contrast,
    density,
    isHighContrast,
    setContrast,
    setDensity,
    toggleContrast,
  };
}
