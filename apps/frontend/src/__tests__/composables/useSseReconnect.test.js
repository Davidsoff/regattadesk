import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { calculateReconnectDelay, createSseConnection } from '../../composables/useSseReconnect'

describe('useSseReconnect', () => {
  describe('calculateReconnectDelay', () => {
    it('returns delay >= MIN_DELAY_MS (100ms)', () => {
      for (let attempt = 0; attempt < 10; attempt++) {
        const delay = calculateReconnectDelay(attempt)
        expect(delay).toBeGreaterThanOrEqual(100)
      }
    })
    
    it('returns delay <= MAX_DELAY_MS (20000ms)', () => {
      // Test large attempt numbers
      for (let attempt = 0; attempt < 20; attempt++) {
        const delay = calculateReconnectDelay(attempt)
        expect(delay).toBeLessThanOrEqual(20000)
      }
    })
    
    it('increases delay with exponential backoff', () => {
      // Run multiple times to account for jitter
      let increasingCount = 0
      const runs = 20
      
      for (let run = 0; run < runs; run++) {
        const delay0 = calculateReconnectDelay(0)
        const delay1 = calculateReconnectDelay(1)
        const delay2 = calculateReconnectDelay(2)
        
        // On average, delays should increase
        if (delay1 > delay0 && delay2 > delay1) {
          increasingCount++
        }
      }
      
      // At least 50% of runs should show increasing pattern (lowered due to jitter)
      expect(increasingCount / runs).toBeGreaterThan(0.5)
    })
    
    it('applies full jitter (returns different values)', () => {
      const delays = new Set()
      
      // Generate 50 delays for attempt 5
      for (let i = 0; i < 50; i++) {
        delays.add(calculateReconnectDelay(5))
      }
      
      // Should have multiple unique values due to jitter
      expect(delays.size).toBeGreaterThan(10)
    })
    
    it('exponential backoff formula: BASE * 2^attempt', () => {
      // For attempt 0: should be between MIN_DELAY and BASE_DELAY
      const delay0 = calculateReconnectDelay(0)
      expect(delay0).toBeGreaterThanOrEqual(100)
      expect(delay0).toBeLessThanOrEqual(500)
      
      // For attempt 1: should be between MIN_DELAY and BASE_DELAY * 2
      const delay1 = calculateReconnectDelay(1)
      expect(delay1).toBeGreaterThanOrEqual(100)
      expect(delay1).toBeLessThanOrEqual(1000)
      
      // For attempt 2: should be between MIN_DELAY and BASE_DELAY * 4
      const delay2 = calculateReconnectDelay(2)
      expect(delay2).toBeGreaterThanOrEqual(100)
      expect(delay2).toBeLessThanOrEqual(2000)
    })
    
    it('caps at MAX_DELAY_MS for large attempts', () => {
      const delay = calculateReconnectDelay(100)
      expect(delay).toBeLessThanOrEqual(20000)
    })
  })
  
  describe('createSseConnection', () => {
    let mockEventSource
    let eventListeners
    
    beforeEach(() => {
      // Reset
      eventListeners = {}
      
      // Mock EventSource
      mockEventSource = {
        addEventListener: vi.fn((event, handler) => {
          eventListeners[event] = handler
        }),
        close: vi.fn(),
        readyState: 1
      }
      
      global.EventSource = vi.fn(function() {
        // Immediately register event listeners
        setTimeout(() => {
          // Simulate async setup
        }, 0)
        return mockEventSource
      })
      
      // Mock console methods
      vi.spyOn(console, 'log').mockImplementation(() => {})
      vi.spyOn(console, 'error').mockImplementation(() => {})
      
      // Use real timers for simplicity
      vi.useRealTimers()
    })
    
    afterEach(() => {
      vi.restoreAllMocks()
    })
    
    it('creates EventSource with correct URL', () => {
      const url = '/public/regattas/123/events'
      createSseConnection(url)
      
      expect(global.EventSource).toHaveBeenCalledWith(
        url,
        expect.objectContaining({ withCredentials: true })
      )
    })
    
    it('registers event listeners', async () => {
      createSseConnection('/test')
      
      // Wait for async setup
      await new Promise(resolve => setTimeout(resolve, 10))
      
      expect(mockEventSource.addEventListener).toHaveBeenCalledWith('open', expect.any(Function))
      expect(mockEventSource.addEventListener).toHaveBeenCalledWith('error', expect.any(Function))
      expect(mockEventSource.addEventListener).toHaveBeenCalledWith('snapshot', expect.any(Function))
      expect(mockEventSource.addEventListener).toHaveBeenCalledWith('draw_revision', expect.any(Function))
      expect(mockEventSource.addEventListener).toHaveBeenCalledWith('results_revision', expect.any(Function))
    })
    
    it('calls onConnectionChange(true) when connection opens', async () => {
      const onConnectionChange = vi.fn()
      createSseConnection('/test', { onConnectionChange })
      
      // Wait for async setup
      await new Promise(resolve => setTimeout(resolve, 10))
      
      // Simulate open event
      eventListeners['open']()
      
      expect(onConnectionChange).toHaveBeenCalledWith(true)
    })
    
    it('calls onSnapshot when snapshot event received', async () => {
      const onSnapshot = vi.fn()
      const snapshotData = { draw_revision: 2, results_revision: 5 }
      
      createSseConnection('/test', { onSnapshot })
      
      // Wait for async setup
      await new Promise(resolve => setTimeout(resolve, 10))
      
      // Simulate snapshot event
      eventListeners['snapshot']({
        data: JSON.stringify(snapshotData),
        lastEventId: '123:2:5:0'
      })
      
      expect(onSnapshot).toHaveBeenCalledWith(snapshotData)
    })
    
    it('calls onDrawRevision when draw_revision event received', async () => {
      const onDrawRevision = vi.fn()
      const eventData = { draw_revision: 3, results_revision: 5, reason: 'Draw published' }
      
      createSseConnection('/test', { onDrawRevision })
      
      // Wait for async setup
      await new Promise(resolve => setTimeout(resolve, 10))
      
      eventListeners['draw_revision']({
        data: JSON.stringify(eventData),
        lastEventId: '123:3:5:1'
      })
      
      expect(onDrawRevision).toHaveBeenCalledWith(eventData)
    })
    
    it('calls onResultsRevision when results_revision event received', async () => {
      const onResultsRevision = vi.fn()
      const eventData = { draw_revision: 3, results_revision: 6, reason: 'Results updated' }
      
      createSseConnection('/test', { onResultsRevision })
      
      // Wait for async setup
      await new Promise(resolve => setTimeout(resolve, 10))
      
      eventListeners['results_revision']({
        data: JSON.stringify(eventData),
        lastEventId: '123:3:6:2'
      })
      
      expect(onResultsRevision).toHaveBeenCalledWith(eventData)
    })
    
    it('stores lastEventId from events', async () => {
      const connection = createSseConnection('/test')
      
      // Wait for async setup
      await new Promise(resolve => setTimeout(resolve, 10))
      
      eventListeners['snapshot']({
        data: JSON.stringify({ draw_revision: 2, results_revision: 5 }),
        lastEventId: '123:2:5:0'
      })
      
      // lastEventId is stored internally
      expect(connection._getLastEventId()).toBe('123:2:5:0')
    })
    
    it('closes connection when close() is called', async () => {
      const onConnectionChange = vi.fn()
      const connection = createSseConnection('/test', { onConnectionChange })
      
      // Wait for async setup
      await new Promise(resolve => setTimeout(resolve, 10))
      
      connection.close()
      
      expect(mockEventSource.close).toHaveBeenCalled()
      expect(onConnectionChange).toHaveBeenCalledWith(false)
    })
    
    it('schedules reconnect on error', async () => {
      const onConnectionChange = vi.fn()
      createSseConnection('/test', { onConnectionChange })
      
      // Wait for event listeners to be registered
      await new Promise(resolve => setTimeout(resolve, 10))
      
      // Simulate error
      eventListeners['error']({})
      
      // Should call onConnectionChange(false)
      expect(onConnectionChange).toHaveBeenCalledWith(false)
      
      // Should close current connection
      expect(mockEventSource.close).toHaveBeenCalled()
    })
    
    it('does not reconnect after close()', async () => {
      const connection = createSseConnection('/test')
      
      // Wait for event listeners
      await new Promise(resolve => setTimeout(resolve, 10))
      
      connection.close()
      
      // Simulate error after close
      eventListeners['error']({})
      
      // Should not create new EventSource
      expect(global.EventSource).toHaveBeenCalledTimes(1)
    })
    
    it('increments reconnect attempt on each reconnection', async () => {
      createSseConnection('/test')
      
      // Wait for initialization
      await new Promise(resolve => setTimeout(resolve, 10))
      
      // Initial connection
      expect(global.EventSource).toHaveBeenCalledTimes(1)
      
      // Simulate error
      eventListeners['error']({})
      
      // Wait for reconnect
      await new Promise(resolve => setTimeout(resolve, 200))
      
      // Should create new EventSource
      expect(global.EventSource).toHaveBeenCalledTimes(2)
    })
    
    it('resets reconnect attempt counter on successful open', async () => {
      const connection = createSseConnection('/test')
      
      // Wait for event listeners
      await new Promise(resolve => setTimeout(resolve, 10))
      
      // Simulate error and reconnect
      eventListeners['error']({})
      
      await new Promise(resolve => setTimeout(resolve, 200))
      
      // Attempt should be 1
      expect(connection._getReconnectAttempt()).toBeGreaterThan(0)
      
      // Simulate successful open
      eventListeners['open']()
      
      // Attempt should be reset to 0
      expect(connection._getReconnectAttempt()).toBe(0)
    })
  })
})
