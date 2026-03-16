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

import { ref } from 'vue';

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
  if (action?.check?.(event.key, orientation)) {
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
