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

import { onMounted } from 'vue';

function getDocumentObject() {
  if (typeof document === 'undefined') {
    return null;
  }

  return document;
}

function ensureLiveRegion(liveRegion) {
  const doc = getDocumentObject();
  if (!doc) {
    return null;
  }

  if (!liveRegion) {
    liveRegion = doc.getElementById('rd-live-announcer');
    
    if (!liveRegion) {
      liveRegion = doc.createElement('div');
      liveRegion.id = 'rd-live-announcer';
      liveRegion.ariaLive = 'polite';
      liveRegion.ariaAtomic = 'true';
      liveRegion.className = 'rd-sr-only';
      doc.body?.appendChild(liveRegion);
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
    if (!liveRegion) {
      return;
    }

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
