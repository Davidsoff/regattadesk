import { describe, expect, it } from 'vitest';
import { createI18n } from 'vue-i18n';
import { mount } from '@vue/test-utils';
import PrintHeader from '../components/print/PrintHeader.vue';

const i18n = createI18n({
  legacy: false,
  locale: 'en',
  fallbackLocale: 'en',
  messages: {
    en: {
      print: {
        generated: 'Generated',
        draw_version: 'Draw Version',
        results_version: 'Results Version',
        page: 'Page',
        of: 'of'
      }
    }
  }
});

describe('PrintHeader', () => {
  it('renders zero-valued revision and page metadata', () => {
    const wrapper = mount(PrintHeader, {
      global: {
        plugins: [i18n]
      },
      props: {
        regattaName: 'Test Regatta',
        drawRevision: 0,
        resultsRevision: 0,
        pageNumber: 0,
        totalPages: 0,
        timestamp: '2026-02-06T14:30:00Z',
        regattaTimezone: 'UTC'
      }
    });

    const text = wrapper.text();
    expect(text).toContain('Draw Version:');
    expect(text).toContain('v0');
    expect(text).toContain('Results Version:');
    expect(text).toContain('Page:');
    expect(text).toContain('Page: 0');
    expect(text).toContain('of 0');
  });

  it('omits optional metadata when values are null', () => {
    const wrapper = mount(PrintHeader, {
      global: {
        plugins: [i18n]
      },
      props: {
        regattaName: 'Test Regatta',
        drawRevision: null,
        resultsRevision: null,
        pageNumber: null,
        totalPages: null,
        timestamp: '2026-02-06T14:30:00Z',
        regattaTimezone: 'UTC'
      }
    });

    const text = wrapper.text();
    expect(text).not.toContain('Draw Version:');
    expect(text).not.toContain('Results Version:');
    expect(text).not.toContain('Page:');
  });
});
