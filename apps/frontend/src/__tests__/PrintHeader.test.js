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

  describe('metadata validation for print exports', () => {
    it('displays all required metadata fields for complete header', () => {
      const wrapper = mount(PrintHeader, {
        global: {
          plugins: [i18n]
        },
        props: {
          regattaName: 'Complete Metadata Regatta',
          drawRevision: 5,
          resultsRevision: 3,
          pageNumber: 2,
          totalPages: 10,
          timestamp: '2026-02-27T15:00:00Z',
          regattaTimezone: 'Europe/Amsterdam'
        }
      });

      const text = wrapper.text();
      
      // Verify regatta name
      expect(text).toContain('Complete Metadata Regatta');
      
      // Verify timestamp is formatted
      expect(text).toContain('Generated:');
      expect(text).toContain('2026-02-27');
      
      // Verify draw revision
      expect(text).toContain('Draw Version:');
      expect(text).toContain('v5');
      
      // Verify results revision
      expect(text).toContain('Results Version:');
      expect(text).toContain('v3');
      
      // Verify page number with total
      expect(text).toContain('Page:');
      expect(text).toContain('2');
      expect(text).toContain('of');
      expect(text).toContain('10');
    });

    it('handles edge case with high revision numbers', () => {
      const wrapper = mount(PrintHeader, {
        global: {
          plugins: [i18n]
        },
        props: {
          regattaName: 'High Revision Regatta',
          drawRevision: 999,
          resultsRevision: 1000,
          timestamp: '2026-02-27T15:00:00Z',
          regattaTimezone: 'UTC'
        }
      });

      const text = wrapper.text();
      expect(text).toContain('v999');
      expect(text).toContain('v1000');
    });

    it('displays page metadata without totalPages when totalPages is null', () => {
      const wrapper = mount(PrintHeader, {
        global: {
          plugins: [i18n]
        },
        props: {
          regattaName: 'Single Page Export',
          pageNumber: 1,
          totalPages: null,
          timestamp: '2026-02-27T15:00:00Z',
          regattaTimezone: 'UTC'
        }
      });

      const text = wrapper.text();
      expect(text).toContain('Page:');
      expect(text).toContain('1');
      expect(text).not.toContain('of');
    });

    it('ensures header is visible for A4 monochrome printing', () => {
      const wrapper = mount(PrintHeader, {
        global: {
          plugins: [i18n]
        },
        props: {
          regattaName: 'Print Test Regatta',
          drawRevision: 1,
          resultsRevision: 1,
          timestamp: '2026-02-27T15:00:00Z',
          regattaTimezone: 'UTC'
        }
      });

      // Verify header structure for print visibility
      const header = wrapper.find('.print-header');
      expect(header.exists()).toBe(true);
      
      // Check that metadata items are present
      const metaItems = wrapper.findAll('.print-header__meta-item');
      expect(metaItems.length).toBeGreaterThan(0);
    });
  });
});
