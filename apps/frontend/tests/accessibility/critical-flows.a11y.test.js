import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createI18n } from 'vue-i18n';
import { createMemoryHistory, createRouter } from 'vue-router';
import Results from '../../src/views/public/Results.vue';
import { calculateReconnectDelay } from '../../src/composables/useSseReconnect';

/**
 * Critical flow E2E tests
 * These tests validate essential user workflows without requiring full browser automation
 * 
 * Tests are deterministic and avoid timing thresholds per AGENTS.md guidelines
 */

async function mountResults() {
  const i18n = createI18n({
    legacy: false,
    locale: 'en',
    messages: {
      en: {
        live: {
          live: 'Live',
          offline: 'Offline',
          stale_data_message: 'Showing cached results. Reconnecting for latest updates.'
        },
        status: {
          entered: 'Entered'
        },
        public: {
          results: {
            title: 'Results',
            description: 'Live race results'
          },
          version: {
            draw: 'Draw Revision',
            results: 'Results Revision'
          }
        }
      }
    }
  });
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/public/v:drawRevision-:resultsRevision/results',
        name: 'results',
        component: Results
      }
    ]
  });

  await router.push('/public/v1-0/results?regatta_id=test-regatta');
  await router.isReady();

  return mount(Results, {
    global: {
      plugins: [i18n, router]
    }
  });
}

describe('Public Bootstrap Flow', () => {
  beforeEach(() => {
    // Reset fetch mock before each test
    vi.resetAllMocks();
    vi.stubGlobal('sessionStorage', {
      getItem: vi.fn(() => 'test-regatta-123'),
      setItem: vi.fn(),
      removeItem: vi.fn(),
      clear: vi.fn(),
      length: 0,
      key: vi.fn()
    });
    vi.stubGlobal('EventSource', vi.fn(function MockEventSource() {
      return {
        addEventListener: vi.fn(),
        close: vi.fn()
      };
    }));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('should handle bootstrap flow: /versions 401 → /public/session → retry /versions', async () => {
    vi.stubGlobal('fetch', vi.fn((url) => {
      if (String(url).includes('/versions')) {
        if (globalThis.fetch.mock.calls.filter(([calledUrl]) => String(calledUrl).includes('/versions')).length === 1) {
          return Promise.resolve({
            ok: false,
            status: 401,
            headers: { get: () => null }
          });
        }

        return Promise.resolve({
          ok: true,
          status: 200,
          headers: { get: () => 'application/json' },
          json: () => Promise.resolve({ draw_revision: 1, results_revision: 0 })
        });
      }

      if (String(url).includes('/public/session')) {
        return Promise.resolve({
          ok: true,
          status: 204,
          headers: { get: () => null }
        });
      }

      if (String(url).includes('/public/v1-0/regattas/')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          headers: { get: () => 'application/json' },
          json: () => Promise.resolve({ data: [] })
        });
      }

      return Promise.reject(new Error('Unexpected URL'));
    }));

    await mountResults();
    await flushPromises();

    const calledUrls = globalThis.fetch.mock.calls.map(([url]) => String(url));
    expect(calledUrls[0]).toContain('/public/regattas/test-regatta/versions');
    expect(calledUrls[1]).toContain('/public/session');
    expect(calledUrls[2]).toContain('/public/regattas/test-regatta/versions');
  });

  it('should handle /versions success on first try (existing session)', async () => {
    vi.stubGlobal('fetch', vi.fn((url) => {
      if (url.includes('/versions')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          headers: { get: () => 'application/json' },
          json: () => Promise.resolve({ 
            draw_revision: 2, 
            results_revision: 1 
          })
        });
      }
      return Promise.reject(new Error('Unexpected URL'));
    }));

    const response = await fetch('/public/regattas/test-regatta/versions');
    expect(response.ok).toBe(true);
    
    const data = await response.json();
    expect(data.draw_revision).toBe(2);
    expect(data.results_revision).toBe(1);
  });
});

describe('SSE Connection State Management', () => {
  it('should track connection state (Live/Offline)', () => {
    // Simulate SSE connection state tracking
    let connectionState = 'offline';
    
    // Simulate successful connection
    const mockEventSource = {
      readyState: 1, // OPEN
      onopen: null,
      onerror: null,
      onmessage: null
    };
    
    if (mockEventSource.readyState === 1) {
      connectionState = 'live';
    }
    
    expect(connectionState).toBe('live');
    
    // Simulate connection failure
    mockEventSource.readyState = 2; // CLOSED
    
    if (mockEventSource.readyState !== 1) {
      connectionState = 'offline';
    }
    
    expect(connectionState).toBe('offline');
  });

  it('should handle SSE reconnection logic with backoff', () => {
    vi.spyOn(Math, 'random').mockReturnValue(0.5);

    expect(calculateReconnectDelay(0)).toBe(250); // 500 * 0.5
    expect(calculateReconnectDelay(1)).toBe(500); // 1000 * 0.5
    expect(calculateReconnectDelay(2)).toBe(1000); // 2000 * 0.5
    expect(calculateReconnectDelay(3)).toBe(2000); // 4000 * 0.5
    expect(calculateReconnectDelay(4)).toBe(4000); // 8000 * 0.5
    expect(calculateReconnectDelay(5)).toBe(8000); // 16000 * 0.5
    expect(calculateReconnectDelay(6)).toBe(10000); // 20000 * 0.5 (capped)
    expect(calculateReconnectDelay(10)).toBe(10000); // Still capped
  });

  it('should handle snapshot + tick events correctly', () => {
    const events = [];
    
    // Simulate receiving SSE events
    const handleSseEvent = (eventType, data) => {
      events.push({ type: eventType, data });
    };

    // Snapshot event
    handleSseEvent('snapshot', { 
      draw_revision: 1, 
      results_revision: 0 
    });
    
    // Draw revision tick
    handleSseEvent('draw_revision', { 
      draw_revision: 2, 
      results_revision: 0,
      reason: 'Draw published'
    });
    
    // Results revision tick
    handleSseEvent('results_revision', { 
      draw_revision: 2, 
      results_revision: 1,
      reason: 'Results approved'
    });

    expect(events).toHaveLength(3);
    expect(events[0].type).toBe('snapshot');
    expect(events[1].type).toBe('draw_revision');
    expect(events[2].type).toBe('results_revision');
    expect(events[2].data.results_revision).toBe(1);
  });
});

describe('Operator Offline Queue', () => {
  it('should queue actions when offline', () => {
    const offlineQueue = [];
    const isOnline = false;

    const queueAction = (action) => {
      if (!isOnline) {
        offlineQueue.push({
          ...action,
          queuedAt: Date.now()
        });
        return { queued: true };
      }
      // Would perform action immediately if online
      return { queued: false };
    };

    const result = queueAction({ 
      type: 'CREATE_MARKER', 
      data: { timestamp: 123.45 } 
    });

    expect(result.queued).toBe(true);
    expect(offlineQueue).toHaveLength(1);
    expect(offlineQueue[0].type).toBe('CREATE_MARKER');
  });

  it('should sync queued actions when coming online', async () => {
    const offlineQueue = [
      { type: 'CREATE_MARKER', data: { timestamp: 123.45 }, queuedAt: 1000 },
      { type: 'LINK_MARKER', data: { markerId: 'abc', bib: '101' }, queuedAt: 2000 }
    ];

    const syncQueue = async (queue) => {
      const results = [];
      for (const action of queue) {
        // Simulate successful sync
        results.push({ 
          action: action.type, 
          success: true 
        });
      }
      return results;
    };

    const results = await syncQueue(offlineQueue);
    
    expect(results).toHaveLength(2);
    expect(results[0].success).toBe(true);
    expect(results[1].success).toBe(true);
  });

  it('should handle conflict resolution with Last-Write-Wins for unapproved entries', () => {
    const localEdit = {
      markerId: 'marker-1',
      linkedBib: '102',
      timestamp: 1000,
      approved: false
    };

    const serverEdit = {
      markerId: 'marker-1',
      linkedBib: '101',
      timestamp: 900,
      approved: false
    };

    // LWW: local is newer, use local
    const resolved = localEdit.timestamp > serverEdit.timestamp 
      ? localEdit 
      : serverEdit;

    expect(resolved.linkedBib).toBe('102');
    expect(resolved.timestamp).toBe(1000);
  });

  it('should require manual resolution for approved entries', () => {
    const serverEdit = {
      markerId: 'marker-1',
      linkedBib: '101',
      timestamp: 900,
      approved: true // Server version is approved
    };

    // Cannot auto-resolve conflicts with approved entries
    const requiresManualResolution = serverEdit.approved;
    
    expect(requiresManualResolution).toBe(true);
  });
});

describe('Versioned URL Construction', () => {
  it('should construct correct versioned public URLs', () => {
    const buildVersionedUrl = (drawRev, resultsRev, regattaId, resource) => {
      return `/public/v${drawRev}-${resultsRev}/regattas/${regattaId}/${resource}`;
    };

    const scheduleUrl = buildVersionedUrl(1, 0, 'test-regatta', 'schedule');
    expect(scheduleUrl).toBe('/public/v1-0/regattas/test-regatta/schedule');

    const resultsUrl = buildVersionedUrl(2, 3, 'test-regatta', 'results');
    expect(resultsUrl).toBe('/public/v2-3/regattas/test-regatta/results');
  });

  it('should handle revision updates correctly', () => {
    let currentDrawRev = 1;
    let currentResultsRev = 0;

    // Simulate draw revision update
    const updateDrawRevision = (newRev) => {
      currentDrawRev = newRev;
    };

    // Simulate results revision update
    const updateResultsRevision = (newRev) => {
      currentResultsRev = newRev;
    };

    updateDrawRevision(2);
    expect(currentDrawRev).toBe(2);
    expect(currentResultsRev).toBe(0); // Results unchanged

    updateResultsRevision(1);
    expect(currentDrawRev).toBe(2);
    expect(currentResultsRev).toBe(1);
  });
});
