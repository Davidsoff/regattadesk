import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import { run as axeRun } from 'axe-core';
import RdTable from '../../src/components/primitives/RdTable.vue';
import RdChip from '../../src/components/primitives/RdChip.vue';

/**
 * Accessibility tests for primitive components
 * Target: WCAG 2.2 AA compliance
 */

describe('RdTable Accessibility', () => {
  it('should have no accessibility violations with minimal data', async () => {
    const wrapper = mount(RdTable, {
      props: {
        caption: 'Test table',
        isEmpty: false,
        loading: false
      },
      slots: {
        header: '<tr><th>Position</th><th>Bib</th><th>Crew</th></tr>',
        default: '<tr><td>1</td><td>101</td><td>Crew A</td></tr>'
      },
      attachTo: document.body
    });

    const results = await axeRun(wrapper.element, {
      rules: {
        // Focus on critical WCAG 2.2 AA rules
        'color-contrast': { enabled: true },
        'table-duplicate-name': { enabled: true },
        'table-fake-caption': { enabled: true },
        'td-headers-attr': { enabled: true },
        'th-has-data-cells': { enabled: true }
      }
    });

    expect(results.violations).toHaveLength(0);
    
    // If violations exist, provide detailed output for debugging
    if (results.violations.length > 0) {
      console.error('Accessibility violations:', 
        results.violations.map(v => ({
          id: v.id,
          impact: v.impact,
          description: v.description,
          nodes: v.nodes.length,
          help: v.help,
          helpUrl: v.helpUrl
        }))
      );
    }
    
    wrapper.unmount();
  });

  it('should have accessible table structure with caption', async () => {
    const wrapper = mount(RdTable, {
      props: {
        caption: 'Race Results'
      },
      slots: {
        header: '<tr><th scope="col">Position</th><th scope="col">Time</th></tr>',
        default: '<tr><td>1</td><td>05:23.45</td></tr>'
      },
      attachTo: document.body
    });

    const results = await axeRun(wrapper.element);
    expect(results.violations).toHaveLength(0);
    wrapper.unmount();
  });

  it('should handle empty state accessibly', async () => {
    const wrapper = mount(RdTable, {
      props: {
        caption: 'Empty Results',
        isEmpty: true,
        emptyText: 'No results available',
        clearable: true
      },
      attachTo: document.body
    });

    const results = await axeRun(wrapper.element);
    expect(results.violations).toHaveLength(0);

    // Verify empty text is present
    expect(wrapper.text()).toContain('No results available');
    wrapper.unmount();
  });

  it('should handle loading state accessibly', async () => {
    const wrapper = mount(RdTable, {
      props: {
        caption: 'Loading Results',
        loading: true,
        skeletonRows: 3
      },
      attachTo: document.body
    });

    const results = await axeRun(wrapper.element);
    expect(results.violations).toHaveLength(0);

    // Verify skeleton rows have aria-hidden
    const skeletons = wrapper.findAll('[aria-hidden="true"]');
    expect(skeletons.length).toBeGreaterThan(0);
    wrapper.unmount();
  });
});

describe('RdChip Accessibility', () => {
  it('should have no accessibility violations with default props', async () => {
    const wrapper = mount(RdChip, {
      props: {
        label: 'Active'
      },
      attachTo: document.body
    });

    const results = await axeRun(wrapper.element, {
      rules: {
        'color-contrast': { enabled: true },
        'button-name': { enabled: true },
        'link-name': { enabled: true }
      }
    });

    expect(results.violations).toHaveLength(0);
    wrapper.unmount();
  });

  it('should meet color contrast requirements for all variants', async () => {
    const variants = ['success', 'info', 'warn', 'danger', 'neutral'];
    
    for (const variant of variants) {
      const wrapper = mount(RdChip, {
        props: {
          label: `${variant} status`,
          variant
        },
        attachTo: document.body
      });

      const results = await axeRun(wrapper.element, {
        rules: {
          'color-contrast': { enabled: true }
        }
      });

      expect(results.violations, `Variant ${variant} should have no contrast violations`).toHaveLength(0);
      wrapper.unmount();
    }
  });

});
