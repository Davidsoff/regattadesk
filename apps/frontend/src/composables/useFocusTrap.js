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

import { ref, onBeforeUnmount, nextTick } from 'vue';

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
    
    triggerElement?.focus?.();
    previousFocusedElement?.focus?.();

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
