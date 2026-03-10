import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { run as axeRun } from 'axe-core';
import Schedule from '../../src/views/public/Schedule.vue';
import Results from '../../src/views/public/Results.vue';
import { createI18n } from 'vue-i18n';
import { createRouter, createMemoryHistory } from 'vue-router';

/**
 * Accessibility tests for public pages
 * Target: WCAG 2.2 AA compliance (mandatory for public flows)
 */

const i18n = createI18n({
  legacy: false,
  locale: 'en',
  messages: {
    en: {
      live: {
        live: 'Live',
        offline: 'Offline'
      },
      public: {
        schedule: {
          title: 'Schedule',
          description: 'Race schedule',
          empty: 'No scheduled races',
          headers: {
            time: 'Start',
            event: 'Event',
            crew: 'Crew',
            club: 'Club',
            bib_lane: 'Bib / Lane',
            status: 'Status'
          },
          recovery: {
            saved_regatta_hint: 'This link is missing regatta context.',
            use_saved_regatta: 'Use saved regatta'
          },
          errors: {
            missing_regatta: 'Missing regatta ID',
            load_failed: 'Failed to load schedule'
          }
        },
        results: {
          title: 'Results',
          description: 'Live race results',
          empty: 'No results published for this revision yet.',
          version_banner: 'Draw v{drawRevision}, Results v{resultsRevision}',
          version_link: 'Copy canonical results link',
          recovery: {
            missing_regatta: 'Missing regatta context.',
            bootstrap_failed: 'Unable to refresh public results right now.',
            retry: 'Retry loading results'
          }
        },
        version: {
          draw: 'Draw Revision',
          results: 'Results Revision'
        }
      },
      live: {
        offline: 'Offline'
      },
      status: {
        active: 'Active',
        withdrawn: 'Withdrawn'
      }
    }
  }
});

const routes = [
  {
    path: '/public/v:drawRevision-:resultsRevision/schedule',
    name: 'schedule',
    component: Schedule
  },
  {
    path: '/public/v:drawRevision-:resultsRevision/results',
    name: 'results',
    component: Results
  }
];

const mockClient = {
  get: vi.fn().mockResolvedValue({ data: [] })
};

vi.mock('../../src/api', () => ({
  createApiClient: () => mockClient
}));

const DEFAULT_QUERY = { regatta_id: 'test-regatta-123' };
const BLOCKING_IMPACTS = new Set(['critical', 'serious']);

function createPublicRouter(routeName, params = {}) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes
  });

  router.push({
    name: routeName,
    params: {
      drawRevision: '1',
      resultsRevision: '0',
      ...params
    },
    query: DEFAULT_QUERY
  });

  return router;
}

async function mountPublicView(routeName, component, params = {}) {
  const router = createPublicRouter(routeName, params);
  await router.isReady();

  const wrapper = mount(component, {
    global: {
      plugins: [i18n, router]
    },
    attachTo: document.body
  });

  await wrapper.vm.$nextTick();
  return wrapper;
}

function getBlockingViolations(results) {
  return results.violations.filter((violation) => BLOCKING_IMPACTS.has(violation.impact));
}

function reportViolations(blockingViolations) {
  if (blockingViolations.length === 0) {
    return;
  }

  console.error(
    'Blocking accessibility violations found:',
    blockingViolations.map((violation) => ({
      id: violation.id,
      impact: violation.impact,
      description: violation.description,
      help: violation.help,
      helpUrl: violation.helpUrl,
      nodes: violation.nodes.length
    }))
  );
}

async function expectNoBlockingViolations(element, axeOptions) {
  const results = await axeRun(element, axeOptions);
  const blockingViolations = getBlockingViolations(results);
  reportViolations(blockingViolations);
  expect(blockingViolations).toHaveLength(0);
}

function stubSessionStorage() {
  vi.stubGlobal('sessionStorage', {
    getItem: vi.fn(() => 'test-regatta-123'),
    setItem: vi.fn(),
    removeItem: vi.fn(),
    clear: vi.fn(),
    length: 0,
    key: vi.fn()
  });
}

function stubFetch() {
  vi.stubGlobal('fetch', vi.fn(() =>
    Promise.resolve({
      ok: true,
      status: 200,
      headers: {
        get: (name) => (name === 'content-type' ? 'application/json' : null)
      },
      json: () => Promise.resolve({ data: [] })
    })
  ));
}

function stubEventSource() {
  vi.stubGlobal('EventSource', vi.fn(function MockEventSource() {
    return {
      addEventListener: vi.fn(),
      close: vi.fn()
    };
  }));
}

describe('Public Schedule Page Accessibility', () => {
  beforeEach(() => {
    mockClient.get.mockReset();
    mockClient.get.mockResolvedValue({ data: [] });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('should have no critical/serious accessibility violations', async () => {
    const wrapper = await mountPublicView('schedule', Schedule);

    await expectNoBlockingViolations(wrapper.element, {
      rules: {
        'color-contrast': { enabled: true },
        'heading-order': { enabled: true },
        'landmark-unique': { enabled: true },
        'page-has-heading-one': { enabled: false },
        'link-name': { enabled: true },
        'button-name': { enabled: true },
        label: { enabled: true },
        'document-title': { enabled: false },
        'html-has-lang': { enabled: false }
      }
    });

    wrapper.unmount();
  });

  it('should have proper heading hierarchy', async () => {
    const wrapper = await mountPublicView('schedule', Schedule);

    const results = await axeRun(wrapper.element, {
      rules: {
        'heading-order': { enabled: true }
      }
    });

    expect(results.violations).toHaveLength(0);
    wrapper.unmount();
  });

  it('should have accessible error messages', async () => {
    mockClient.get.mockRejectedValueOnce(new Error('Network error'));

    const wrapper = await mountPublicView('schedule', Schedule);
    await wrapper.vm.$nextTick();
    await flushPromises();

    const errorElement = wrapper.find('[role="alert"]');
    if (errorElement.exists()) {
      const results = await axeRun(errorElement.element);
      expect(results.violations).toHaveLength(0);
    }

    wrapper.unmount();
  });
});

describe('Public Results Page Accessibility', () => {
  beforeEach(() => {
    stubSessionStorage();
    stubFetch();
    stubEventSource();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('should have no critical/serious accessibility violations', async () => {
    const wrapper = await mountPublicView('results', Results, { resultsRevision: '1' });

    await expectNoBlockingViolations(wrapper.element, {
      rules: {
        'color-contrast': { enabled: true },
        'heading-order': { enabled: true },
        'link-name': { enabled: true },
        'button-name': { enabled: true }
      }
    });

    wrapper.unmount();
  });

  it('should have accessible live status indicator', async () => {
    const wrapper = await mountPublicView('results', Results, { resultsRevision: '1' });
    await expectNoBlockingViolations(wrapper.element);
    wrapper.unmount();
  });
});
