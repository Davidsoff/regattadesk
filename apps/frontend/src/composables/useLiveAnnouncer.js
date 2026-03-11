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

import { ref, onMounted } from 'vue';

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
