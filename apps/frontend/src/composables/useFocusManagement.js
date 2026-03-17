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

import { nextTick } from 'vue';
import { focusTemporarily } from './useSkipLink.js';

async function focusPageHeading(selector = 'h1') {
  await nextTick();
  const heading = document.querySelector(selector);
  if (heading) {
    focusTemporarily(heading);
  }
}

async function focusFirstError(containerSelector = '[role="alert"], .error-summary') {
  await nextTick();
  const errorContainer = document.querySelector(containerSelector);
  if (errorContainer) {
    focusTemporarily(errorContainer);
  }
}

async function focusElement(selector) {
  await nextTick();
  const element = document.querySelector(selector);
  element?.focus();
}

export function useFocusManagement() {
  return {
    focusPageHeading,
    focusFirstError,
    focusElement,
  };
}
