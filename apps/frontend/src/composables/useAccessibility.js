/**
 * Accessibility composables for RegattaDesk
 * 
 * Provides utilities for focus management, keyboard navigation,
 * and ARIA announcements aligned with WCAG 2.2 AA requirements.
 */

import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue';

/**
 * Focus trap for modals and drawers
 * 
 * Traps keyboard focus within an element and returns focus
 * to the trigger element when released.
 * 
 * Usage:
 * const { trapRef, activate, deactivate } = useFocusTrap();
 * 
 * <div ref="trapRef">...</div>
 * activate(triggerElement);
 * // Later: deactivate();
 */
export function useFocusTrap() {
  const trapRef = ref(null);
  let triggerElement = null;
  let previousFocusedElement = null;

  const focusableSelector = [
    'a[href]',
    'button:not([disabled])',
    'textarea:not([disabled])',
    'input:not([disabled])',
    'select:not([disabled])',
    '[tabindex]:not([tabindex="-1"])',
  ].join(', ');

  function getFocusableElements() {
    if (!trapRef.value) return [];
    return Array.from(trapRef.value.querySelectorAll(focusableSelector));
  }

  function handleKeyDown(event) {
    if (event.key !== 'Tab' || !trapRef.value) return;

    const focusableElements = getFocusableElements();
    if (focusableElements.length === 0) return;

    const firstElement = focusableElements[0];
    const lastElement = focusableElements.at(-1);

    if (event.shiftKey && document.activeElement === firstElement) {
      event.preventDefault();
      lastElement.focus();
    } else if (!event.shiftKey && document.activeElement === lastElement) {
      event.preventDefault();
      firstElement.focus();
    }
  }

  async function activate(trigger = null) {
    triggerElement = trigger;
    previousFocusedElement = document.activeElement;
    
    await nextTick();
    
    const focusableElements = getFocusableElements();
    if (focusableElements.length > 0) {
      focusableElements[0].focus();
    }

    document.addEventListener('keydown', handleKeyDown);
  }

  function deactivate() {
    document.removeEventListener('keydown', handleKeyDown);
    
    if (triggerElement && typeof triggerElement.focus === 'function') {
      triggerElement.focus();
    } else if (previousFocusedElement && typeof previousFocusedElement.focus === 'function') {
      previousFocusedElement.focus();
    }

    triggerElement = null;
    previousFocusedElement = null;
  }

  onBeforeUnmount(() => {
    deactivate();
  });

  return {
    trapRef,
    activate,
    deactivate,
  };
}

/**
 * Live region announcer for screen readers
 * 
 * Creates an ARIA live region for making announcements to screen readers.
 * Useful for dynamic content updates, filter results, etc.
 * 
 * Usage:
 * const { announce } = useLiveAnnouncer();
 * announce('12 entries selected', 'polite');
 */

// Helper function moved to module scope
function ensureLiveRegion(liveRegion) {
  if (!liveRegion) {
    liveRegion = document.getElementById('rd-live-announcer');
    
    if (!liveRegion) {
      liveRegion = document.createElement('div');
      liveRegion.id = 'rd-live-announcer';
      liveRegion.ariaLive = 'polite';
      liveRegion.ariaAtomic = 'true';
      liveRegion.className = 'rd-sr-only';
      document.body.appendChild(liveRegion);
    }
  }
  return liveRegion;
}

export function useLiveAnnouncer() {
  let liveRegion = null;

  /**
   * Announce a message to screen readers
   * 
   * @param {string} message - Message to announce
   * @param {'polite'|'assertive'} priority - Announcement priority
   */
  function announce(message, priority = 'polite') {
    liveRegion = ensureLiveRegion(liveRegion);
    liveRegion.ariaLive = priority;
    
    // Clear and re-add to ensure announcement
    liveRegion.textContent = '';
    setTimeout(() => {
      liveRegion.textContent = message;
    }, 100);
  }

  onMounted(() => {
    liveRegion = ensureLiveRegion(liveRegion);
  });

  return {
    announce,
  };
}

/**
 * Keyboard navigation for arrow keys
 * 
 * Handles arrow key navigation within a list or grid.
 * Useful for tables, event matrices, etc.
 * 
 * Usage:
 * const { containerRef, handleKeyDown } = useArrowNavigation({
 *   orientation: 'vertical',
 *   itemSelector: 'tr[tabindex]',
 * });
 * 
 * <table ref="containerRef" @keydown="handleKeyDown">...</table>
 */

// Helper functions moved to module scope
function calculateNextIndex(currentIndex, direction, itemsLength, loop) {
  let nextIndex = currentIndex;
  
  if (direction === 'forward') {
    nextIndex = currentIndex + 1;
    if (loop && nextIndex >= itemsLength) {
      nextIndex = 0;
    }
  } else if (direction === 'backward') {
    nextIndex = currentIndex - 1;
    if (loop && nextIndex < 0) {
      nextIndex = itemsLength - 1;
    }
  }
  
  return nextIndex;
}

function shouldHandleVertical(key, orientation) {
  return (key === 'ArrowDown' || key === 'ArrowUp') && 
         (orientation === 'vertical' || orientation === 'both');
}

function shouldHandleHorizontal(key, orientation) {
  return (key === 'ArrowRight' || key === 'ArrowLeft') && 
         (orientation === 'horizontal' || orientation === 'both');
}

function handleArrowNavigation(event, currentIndex, items, loop, orientation) {
  let nextIndex = currentIndex;
  let handled = false;

  const directionMap = {
    'ArrowDown': { check: shouldHandleVertical, direction: 'forward' },
    'ArrowUp': { check: shouldHandleVertical, direction: 'backward' },
    'ArrowRight': { check: shouldHandleHorizontal, direction: 'forward' },
    'ArrowLeft': { check: shouldHandleHorizontal, direction: 'backward' },
  };

  const action = directionMap[event.key];
  if (action && action.check(event.key, orientation)) {
    event.preventDefault();
    nextIndex = calculateNextIndex(currentIndex, action.direction, items.length, loop);
    handled = true;
  } else if (event.key === 'Home') {
    event.preventDefault();
    nextIndex = 0;
    handled = true;
  } else if (event.key === 'End') {
    event.preventDefault();
    nextIndex = items.length - 1;
    handled = true;
  }

  return { nextIndex, handled };
}

export function useArrowNavigation(options = {}) {
  const {
    orientation = 'vertical', // 'vertical', 'horizontal', or 'both'
    itemSelector = '[tabindex="0"], [tabindex="-1"]',
    loop = false,
  } = options;

  const containerRef = ref(null);

  function getItems() {
    if (!containerRef.value) return [];
    return Array.from(containerRef.value.querySelectorAll(itemSelector));
  }

  function getCurrentIndex() {
    const items = getItems();
    return items.indexOf(document.activeElement);
  }

  function focusItem(index) {
    const items = getItems();
    if (index >= 0 && index < items.length) {
      items[index].focus();
    }
  }

  function handleKeyDown(event) {
    const currentIndex = getCurrentIndex();
    if (currentIndex === -1) return;

    const items = getItems();
    const { nextIndex, handled } = handleArrowNavigation(event, currentIndex, items, loop, orientation);

    if (handled && nextIndex !== currentIndex && nextIndex >= 0 && nextIndex < items.length) {
      focusItem(nextIndex);
    }
  }

  return {
    containerRef,
    handleKeyDown,
  };
}

/**
 * Skip link utility
 * 
 * Creates a skip-to-main-content link for keyboard navigation.
 * The link appears on focus for keyboard users.
 * 
 * Usage:
 * const { skipToMain } = useSkipLink();
 * 
 * <a href="#" @click.prevent="skipToMain" class="rd-skip-link">Skip to main content</a>
 */

// Helper function moved to module scope
function skipToMain(mainId = 'main-content') {
  const mainElement = document.getElementById(mainId);
  if (mainElement) {
    mainElement.tabIndex = -1;
    mainElement.focus();
    
    // Remove tabindex after focus moves away
    const cleanup = () => {
      delete mainElement.dataset.tempTabindex;
      mainElement.tabIndex = 0;
      mainElement.removeEventListener('blur', cleanup);
      mainElement.removeEventListener('focusout', cleanup);
    };
    
    mainElement.dataset.tempTabindex = 'true';
    mainElement.addEventListener('blur', cleanup, { once: true });
    mainElement.addEventListener('focusout', cleanup, { once: true });
  }
}

export function useSkipLink() {
  return {
    skipToMain,
  };
}

/**
 * Focus management for page transitions
 * 
 * Manages focus when navigating between pages or after dynamic content updates.
 * 
 * Usage:
 * const { focusPageHeading, focusFirstError } = useFocusManagement();
 * 
 * // After route change:
 * focusPageHeading();
 * 
 * // After form submission with errors:
 * focusFirstError();
 */

// Helper functions moved to module scope
async function focusPageHeading(selector = 'h1') {
  await nextTick();
  const heading = document.querySelector(selector);
  if (heading) {
    heading.tabIndex = -1;
    heading.focus();
    
    const cleanup = () => {
      delete heading.dataset.tempTabindex;
      heading.tabIndex = 0;
      heading.removeEventListener('blur', cleanup);
      heading.removeEventListener('focusout', cleanup);
    };
    
    heading.dataset.tempTabindex = 'true';
    heading.addEventListener('blur', cleanup, { once: true });
    heading.addEventListener('focusout', cleanup, { once: true });
  }
}

async function focusFirstError(containerSelector = '[role="alert"], .error-summary') {
  await nextTick();
  const errorContainer = document.querySelector(containerSelector);
  if (errorContainer) {
    errorContainer.tabIndex = -1;
    errorContainer.focus();
    
    const cleanup = () => {
      delete errorContainer.dataset.tempTabindex;
      errorContainer.tabIndex = 0;
      errorContainer.removeEventListener('blur', cleanup);
      errorContainer.removeEventListener('focusout', cleanup);
    };
    
    errorContainer.dataset.tempTabindex = 'true';
    errorContainer.addEventListener('blur', cleanup, { once: true });
    errorContainer.addEventListener('focusout', cleanup, { once: true });
  }
}

async function focusElement(selector) {
  await nextTick();
  const element = document.querySelector(selector);
  if (element) {
    element.focus();
  }
}

export function useFocusManagement() {
  return {
    focusPageHeading,
    focusFirstError,
    focusElement,
  };
}
