/**
 * Skip link utility
 *
 * Creates a skip-to-main-content link for keyboard navigation.
 * The link appears on focus for keyboard users.
 *
 * Usage:
 * const { skipToMain } = createSkipLink();
 *
 * <a href="#" @click.prevent="skipToMain" class="rd-skip-link">Skip</a>
 */

import { ref } from 'vue';

/**
 * Temporarily focus an element, restoring its original tabIndex when focus leaves.
 * @param {HTMLElement} el
 */
export function focusTemporarily(el) {
  const originalTabIndex = el.getAttribute('tabindex');
  el.tabIndex = -1;
  el.focus();

  const cleanup = () => {
    if (originalTabIndex === null) {
      el.removeAttribute('tabindex');
    } else {
      el.setAttribute('tabindex', originalTabIndex);
    }
    el.removeEventListener('blur', cleanup);
    el.removeEventListener('focusout', cleanup);
  };

  el.addEventListener('blur', cleanup, { once: true });
  el.addEventListener('focusout', cleanup, { once: true });
}

function skipToMain(mainId = 'main-content') {
  const mainElement = document.getElementById(mainId);
  if (mainElement) {
    focusTemporarily(mainElement);
  }
}

export function createSkipLink() {
  return {
    skipToMain,
  };
}
