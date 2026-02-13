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
    const lastElement = focusableElements[focusableElements.length - 1];

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
export function useLiveAnnouncer() {
  let liveRegion = null;

  function ensureLiveRegion() {
    if (!liveRegion) {
      liveRegion = document.getElementById('rd-live-announcer');
      
      if (!liveRegion) {
        liveRegion = document.createElement('div');
        liveRegion.id = 'rd-live-announcer';
        liveRegion.setAttribute('aria-live', 'polite');
        liveRegion.setAttribute('aria-atomic', 'true');
        liveRegion.className = 'rd-sr-only';
        document.body.appendChild(liveRegion);
      }
    }
    return liveRegion;
  }

  /**
   * Announce a message to screen readers
   * 
   * @param {string} message - Message to announce
   * @param {'polite'|'assertive'} priority - Announcement priority
   */
  function announce(message, priority = 'polite') {
    const region = ensureLiveRegion();
    region.setAttribute('aria-live', priority);
    
    // Clear and re-add to ensure announcement
    region.textContent = '';
    setTimeout(() => {
      region.textContent = message;
    }, 100);
  }

  onMounted(() => {
    ensureLiveRegion();
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
    return items.findIndex(item => item === document.activeElement);
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
    let nextIndex = currentIndex;

    switch (event.key) {
      case 'ArrowDown':
        if (orientation === 'vertical' || orientation === 'both') {
          event.preventDefault();
          nextIndex = currentIndex + 1;
          if (loop && nextIndex >= items.length) {
            nextIndex = 0;
          }
        }
        break;
      
      case 'ArrowUp':
        if (orientation === 'vertical' || orientation === 'both') {
          event.preventDefault();
          nextIndex = currentIndex - 1;
          if (loop && nextIndex < 0) {
            nextIndex = items.length - 1;
          }
        }
        break;
      
      case 'ArrowRight':
        if (orientation === 'horizontal' || orientation === 'both') {
          event.preventDefault();
          nextIndex = currentIndex + 1;
          if (loop && nextIndex >= items.length) {
            nextIndex = 0;
          }
        }
        break;
      
      case 'ArrowLeft':
        if (orientation === 'horizontal' || orientation === 'both') {
          event.preventDefault();
          nextIndex = currentIndex - 1;
          if (loop && nextIndex < 0) {
            nextIndex = items.length - 1;
          }
        }
        break;
      
      case 'Home':
        event.preventDefault();
        nextIndex = 0;
        break;
      
      case 'End':
        event.preventDefault();
        nextIndex = items.length - 1;
        break;
    }

    if (nextIndex !== currentIndex && nextIndex >= 0 && nextIndex < items.length) {
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
export function useSkipLink() {
  function skipToMain(mainId = 'main-content') {
    const mainElement = document.getElementById(mainId);
    if (mainElement) {
      mainElement.setAttribute('tabindex', '-1');
      mainElement.focus();
      
      // Remove tabindex after focus moves away or after a short delay
      const cleanup = () => {
        mainElement.removeAttribute('tabindex');
        mainElement.removeEventListener('blur', cleanup);
        mainElement.removeEventListener('focusout', cleanup);
      };
      
      mainElement.addEventListener('blur', cleanup, { once: true });
      mainElement.addEventListener('focusout', cleanup, { once: true });
    }
  }

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
export function useFocusManagement() {
  async function focusPageHeading(selector = 'h1') {
    await nextTick();
    const heading = document.querySelector(selector);
    if (heading) {
      heading.setAttribute('tabindex', '-1');
      heading.focus();
      
      const cleanup = () => {
        heading.removeAttribute('tabindex');
        heading.removeEventListener('blur', cleanup);
        heading.removeEventListener('focusout', cleanup);
      };
      
      heading.addEventListener('blur', cleanup, { once: true });
      heading.addEventListener('focusout', cleanup, { once: true });
    }
  }

  async function focusFirstError(containerSelector = '[role="alert"], .error-summary') {
    await nextTick();
    const errorContainer = document.querySelector(containerSelector);
    if (errorContainer) {
      errorContainer.setAttribute('tabindex', '-1');
      errorContainer.focus();
      
      const cleanup = () => {
        errorContainer.removeAttribute('tabindex');
        errorContainer.removeEventListener('blur', cleanup);
        errorContainer.removeEventListener('focusout', cleanup);
      };
      
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

  return {
    focusPageHeading,
    focusFirstError,
    focusElement,
  };
}
