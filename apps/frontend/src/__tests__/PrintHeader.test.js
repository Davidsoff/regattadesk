import { beforeEach, describe, expect, it } from 'vitest';
import { createI18n } from 'vue-i18n';
import { mount } from '@vue/test-utils';
import { nextTick } from 'vue';
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
    },
    nl: {
      print: {
        generated: 'Gegenereerd',
        draw_version: 'Lotingversie',
        results_version: 'Resultatenversie',
        page: 'Pagina',
        of: 'van'
      }
    }
  }
});

describe('PrintHeader', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en';
  });

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
    expect(text).toContain('Generated: 2026-02-06 14:30');
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

  it('reacts to locale changes after mount for generated timestamp', async () => {
    const wrapper = mount(PrintHeader, {
      global: {
        plugins: [i18n]
      },
      props: {
        regattaName: 'Locale Switching Regatta',
        drawRevision: 1,
        resultsRevision: 2,
        pageNumber: 1,
        totalPages: 2,
        timestamp: '2026-02-06T14:30:00Z',
        regattaTimezone: 'UTC'
      }
    });

    expect(wrapper.text()).toContain('Generated: 2026-02-06 14:30');

    i18n.global.locale.value = 'nl';
    await nextTick();

    const text = wrapper.text();
    expect(text).toContain('Gegenereerd: 06-02-2026 14:30');
    expect(text).toContain('Lotingversie: v1');
    expect(text).toContain('Resultatenversie: v2');
    expect(text).toContain('Pagina: 1');
    expect(text).toContain('van 2');
  });

  it('formats generated timestamp using provided regatta timezone', () => {
    const wrapper = mount(PrintHeader, {
      global: {
        plugins: [i18n]
      },
      props: {
        regattaName: 'Timezone Regatta',
        timestamp: '2026-03-29T01:30:00Z',
        regattaTimezone: 'Europe/Amsterdam'
      }
    });

    expect(wrapper.text()).toContain('Generated: 2026-03-29 03:30');
  });
});
