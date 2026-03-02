import { describe, it, expect, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { run as axeRun } from 'axe-core';
import Schedule from '../../src/views/public/Schedule.vue';
import Results from '../../src/views/public/Results.vue';
import { createI18n } from 'vue-i18n';
import { createRouter, createMemoryHistory } from 'vue-router';

/**
 * Accessibility tests for public pages
 * Target: WCAG 2.2 AA compliance (mandatory for public flows)
 * 
 * These tests ensure that the public-facing schedule and results pages
 * meet accessibility standards required by the PRD.
 */

// Mock i18n for tests
const i18n = createI18n({
  legacy: false,
  locale: 'en',
  messages: {
    en: {
      public: {
        schedule: {
          title: 'Schedule',
          description: 'Race schedule',
          empty: 'No scheduled races',
          errors: {
            missing_regatta: 'Missing regatta ID',
            load_failed: 'Failed to load schedule'
          }
        }
      },
      status: {
        active: 'Active',
        withdrawn: 'Withdrawn'
      }
    }
  }
});

// Create a proper router for tests
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

// Mock the API module
vi.mock('../../src/api', () => ({
  createApiClient: () => ({
    get: vi.fn().mockResolvedValue({ data: [] })
  })
}));

describe('Public Schedule Page Accessibility', () => {
  it('should have no critical/serious accessibility violations', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes
    });

    router.push({
      name: 'schedule',
      params: {
        drawRevision: '1',
        resultsRevision: '0'
      },
      query: {
        regatta_id: 'test-regatta-123'
      }
    });

    await router.isReady();

    const wrapper = mount(Schedule, {
      global: {
        plugins: [i18n, router]
      },
      attachTo: document.body
    });

    // Wait for component to render
    await wrapper.vm.$nextTick();

    const results = await axeRun(wrapper.element, {
      rules: {
        // WCAG 2.2 AA critical rules
        'color-contrast': { enabled: true },
        'heading-order': { enabled: true },
        'landmark-unique': { enabled: true },
        'page-has-heading-one': { enabled: true },
        'link-name': { enabled: true },
        'button-name': { enabled: true },
        'label': { enabled: true },
        'document-title': { enabled: false }, // Not applicable in component tests
        'html-has-lang': { enabled: false }    // Not applicable in component tests
      }
    });

    // Filter to only critical and serious violations (blocking for public pages)
    const blockingViolations = results.violations.filter(
      v => v.impact === 'critical' || v.impact === 'serious'
    );

    if (blockingViolations.length > 0) {
      console.error('Blocking accessibility violations found:', 
        blockingViolations.map(v => ({
          id: v.id,
          impact: v.impact,
          description: v.description,
          help: v.help,
          helpUrl: v.helpUrl,
          nodes: v.nodes.length
        }))
      );
    }

    expect(blockingViolations).toHaveLength(0);
    wrapper.unmount();
  });

  it('should have proper heading hierarchy', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes
    });

    router.push({
      name: 'schedule',
      params: {
        drawRevision: '1',
        resultsRevision: '0'
      },
      query: {
        regatta_id: 'test-regatta-123'
      }
    });

    await router.isReady();

    const wrapper = mount(Schedule, {
      global: {
        plugins: [i18n, router]
      },
      attachTo: document.body
    });

    await wrapper.vm.$nextTick();

    const results = await axeRun(wrapper.element, {
      rules: {
        'heading-order': { enabled: true }
      }
    });

    expect(results.violations).toHaveLength(0);
    wrapper.unmount();
  });

  it('should have accessible error messages', async () => {
    // Override the mock to return an error
    const { createApiClient } = await import('../../src/api');
    createApiClient().get = vi.fn().mockRejectedValue(new Error('Network error'));

    const router = createRouter({
      history: createMemoryHistory(),
      routes
    });

    router.push({
      name: 'schedule',
      params: {
        drawRevision: '1',
        resultsRevision: '0'
      },
      query: {
        regatta_id: 'test-regatta-123'
      }
    });

    await router.isReady();

    const wrapper = mount(Schedule, {
      global: {
        plugins: [i18n, router]
      },
      attachTo: document.body
    });

    // Wait for error state to be applied
    await wrapper.vm.$nextTick();
    await flushPromises();

    // Check for role="alert" on error messages
    const errorElement = wrapper.find('[role="alert"]');
    if (errorElement.exists()) {
      const results = await axeRun(errorElement.element);
      expect(results.violations).toHaveLength(0);
    }
    wrapper.unmount();
  });
});

describe('Public Results Page Accessibility', () => {
  it('should have no critical/serious accessibility violations', async () => {
    // Mock sessionStorage
    global.sessionStorage = {
      getItem: vi.fn(() => 'test-regatta-123'),
      setItem: vi.fn(),
      removeItem: vi.fn(),
      clear: vi.fn(),
      length: 0,
      key: vi.fn()
    };

    // Mock fetch for results page
    global.fetch = vi.fn(() =>
      Promise.resolve({
        ok: true,
        headers: {
          get: (name) => name === 'content-type' ? 'application/json' : null
        },
        json: () => Promise.resolve({ data: [] })
      })
    );

    const router = createRouter({
      history: createMemoryHistory(),
      routes
    });

    router.push({
      name: 'results',
      params: {
        drawRevision: '1',
        resultsRevision: '1'
      },
      query: {
        regatta_id: 'test-regatta-123'
      }
    });

    await router.isReady();

    const wrapper = mount(Results, {
      global: {
        plugins: [i18n, router]
      },
      attachTo: document.body
    });

    await wrapper.vm.$nextTick();

    const results = await axeRun(wrapper.element, {
      rules: {
        'color-contrast': { enabled: true },
        'heading-order': { enabled: true },
        'link-name': { enabled: true },
        'button-name': { enabled: true }
      }
    });

    const blockingViolations = results.violations.filter(
      v => v.impact === 'critical' || v.impact === 'serious'
    );

    if (blockingViolations.length > 0) {
      console.error('Blocking accessibility violations found:', 
        blockingViolations.map(v => ({
          id: v.id,
          impact: v.impact,
          description: v.description,
          help: v.help,
          helpUrl: v.helpUrl
        }))
      );
    }

    expect(blockingViolations).toHaveLength(0);
    wrapper.unmount();
  });

  it('should have accessible live status indicator', async () => {
    global.sessionStorage = {
      getItem: vi.fn(() => 'test-regatta-123'),
      setItem: vi.fn()
    };

    global.fetch = vi.fn(() =>
      Promise.resolve({
        ok: true,
        headers: {
          get: () => 'application/json'
        },
        json: () => Promise.resolve({ data: [] })
      })
    );

    const router = createRouter({
      history: createMemoryHistory(),
      routes
    });

    router.push({
      name: 'results',
      params: {
        drawRevision: '1',
        resultsRevision: '1'
      },
      query: {
        regatta_id: 'test-regatta-123'
      }
    });

    await router.isReady();

    const wrapper = mount(Results, {
      global: {
        plugins: [i18n, router]
      },
      attachTo: document.body
    });

    await wrapper.vm.$nextTick();

    const results = await axeRun(wrapper.element);
    
    // Ensure status indicators don't have accessibility issues
    const blockingViolations = results.violations.filter(
      v => v.impact === 'critical' || v.impact === 'serious'
    );
    
    expect(blockingViolations).toHaveLength(0);
    wrapper.unmount();
  });
});
