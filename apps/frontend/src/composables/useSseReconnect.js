/**
 * RegattaDesk SSE Reconnect Helper
 * 
 * Implements BC05-003 reconnect policy:
 * - Min delay: 100ms
 * - Base delay: 500ms
 * - Max delay cap: 20s
 * - Full jitter
 * - Retry forever
 * 
 * Usage:
 * ```js
 * import { createSseConnection } from './useSseReconnect'
 * 
 * const connection = createSseConnection('/public/regattas/123/events', {
 *   onSnapshot: (data) => console.log('Snapshot:', data),
 *   onDrawRevision: (data) => console.log('Draw updated:', data),
 *   onResultsRevision: (data) => console.log('Results updated:', data),
 *   onConnectionChange: (connected) => console.log('Connected:', connected)
 * })
 * 
 * // Later, when done:
 * connection.close()
 * ```
 */

import { ref, onMounted, onUnmounted } from 'vue'

const MIN_DELAY_MS = 100
const BASE_DELAY_MS = 500
const MAX_DELAY_MS = 20000

/**
 * Calculates next reconnect delay with exponential backoff and full jitter.
 * 
 * Formula: min(MAX_DELAY, random(0, min(MAX_DELAY, BASE * 2^attempt)))
 * 
 * @param {number} attempt - The current attempt number (0-indexed)
 * @returns {number} Delay in milliseconds
 */
export function calculateReconnectDelay(attempt) {
  // Exponential backoff: BASE * 2^attempt
  const exponentialDelay = BASE_DELAY_MS * Math.pow(2, attempt)
  
  // Cap at MAX_DELAY_MS
  const cappedDelay = Math.min(MAX_DELAY_MS, exponentialDelay)
  
  // Full jitter: random between 0 and cappedDelay
  const jitteredDelay = Math.random() * cappedDelay
  
  // Ensure minimum delay
  return Math.max(MIN_DELAY_MS, jitteredDelay)
}

/**
 * Creates an SSE connection with automatic reconnection.
 * 
 * @param {string} url - SSE endpoint URL
 * @param {Object} options - Connection options
 * @param {Function} options.onSnapshot - Called when snapshot event received
 * @param {Function} options.onDrawRevision - Called when draw_revision event received
 * @param {Function} options.onResultsRevision - Called when results_revision event received
 * @param {Function} options.onConnectionChange - Called when connection state changes
 * @param {Function} options.onError - Called when error occurs
 * @returns {Object} Connection controller with close() method
 */
export function createSseConnection(url, options = {}) {
  let eventSource = null
  let reconnectAttempt = 0
  let reconnectTimeout = null
  let isClosed = false
  let lastEventId = null
  
  const {
    onSnapshot = () => {},
    onDrawRevision = () => {},
    onResultsRevision = () => {},
    onConnectionChange = () => {},
    onError = () => {}
  } = options
  
  function connect() {
    if (isClosed) {
      return
    }
    
    // EventSource automatically sends Last-Event-ID header on reconnection
    // Browser handles this transparently when EventSource reconnects
    try {
      eventSource = new EventSource(url, {
        withCredentials: true
      })
      
      eventSource.addEventListener('open', handleOpen)
      eventSource.addEventListener('error', handleError)
      eventSource.addEventListener('snapshot', handleSnapshot)
      eventSource.addEventListener('draw_revision', handleDrawRevision)
      eventSource.addEventListener('results_revision', handleResultsRevision)
      
    } catch (error) {
      console.error('Failed to create EventSource:', error)
      scheduleReconnect()
    }
  }
  
  function handleOpen() {
    console.log('SSE connection opened')
    reconnectAttempt = 0
    onConnectionChange(true)
  }
  
  function handleError(event) {
    console.error('SSE connection error:', event)
    onConnectionChange(false)
    onError(event)
    
    // Close current connection and schedule reconnect
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
    
    if (!isClosed) {
      scheduleReconnect()
    }
  }
  
  function handleSnapshot(event) {
    try {
      const data = JSON.parse(event.data)
      lastEventId = event.lastEventId
      onSnapshot(data)
    } catch (error) {
      console.error('Failed to parse snapshot event:', error)
    }
  }
  
  function handleDrawRevision(event) {
    try {
      const data = JSON.parse(event.data)
      lastEventId = event.lastEventId
      onDrawRevision(data)
    } catch (error) {
      console.error('Failed to parse draw_revision event:', error)
    }
  }
  
  function handleResultsRevision(event) {
    try {
      const data = JSON.parse(event.data)
      lastEventId = event.lastEventId
      onResultsRevision(data)
    } catch (error) {
      console.error('Failed to parse results_revision event:', error)
    }
  }
  
  function scheduleReconnect() {
    if (reconnectTimeout) {
      clearTimeout(reconnectTimeout)
    }
    
    const delay = calculateReconnectDelay(reconnectAttempt)
    console.log(`Reconnecting in ${Math.round(delay)}ms (attempt ${reconnectAttempt + 1})`)
    
    reconnectTimeout = setTimeout(() => {
      reconnectAttempt++
      connect()
    }, delay)
  }
  
  function close() {
    isClosed = true
    
    if (reconnectTimeout) {
      clearTimeout(reconnectTimeout)
      reconnectTimeout = null
    }
    
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
    
    onConnectionChange(false)
  }
  
  // Start initial connection
  connect()
  
  // Return controller
  return {
    close,
    // Expose for testing
    _getReconnectAttempt: () => reconnectAttempt,
    _getLastEventId: () => lastEventId
  }
}

/**
 * Vue composable for SSE connection management.
 * 
 * @param {string} url - SSE endpoint URL
 * @returns {Object} Reactive SSE connection state and methods
 */
export function useSseConnection(url) {
  const isConnected = ref(false)
  const snapshotData = ref(null)
  const lastDrawRevision = ref(null)
  const lastResultsRevision = ref(null)
  const error = ref(null)
  
  let connection = null
  
  onMounted(() => {
    connection = createSseConnection(url, {
      onSnapshot: (data) => {
        snapshotData.value = data
        lastDrawRevision.value = data.draw_revision
        lastResultsRevision.value = data.results_revision
      },
      onDrawRevision: (data) => {
        lastDrawRevision.value = data.draw_revision
        lastResultsRevision.value = data.results_revision
      },
      onResultsRevision: (data) => {
        lastDrawRevision.value = data.draw_revision
        lastResultsRevision.value = data.results_revision
      },
      onConnectionChange: (connected) => {
        isConnected.value = connected
      },
      onError: (err) => {
        error.value = err
      }
    })
  })
  
  onUnmounted(() => {
    if (connection) {
      connection.close()
    }
  })
  
  return {
    isConnected,
    snapshotData,
    lastDrawRevision,
    lastResultsRevision,
    error
  }
}
