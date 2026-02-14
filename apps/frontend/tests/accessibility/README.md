# Frontend Accessibility Testing Examples

This directory contains examples and guidance for accessibility testing in RegattaDesk.

## Setup

To enable accessibility testing, add these dependencies to `package.json`:

```json
{
  "devDependencies": {
    "vitest": "2.1.8",
    "@vitest/ui": "2.1.8",
    "axe-core": "4.10.2",
    "@axe-core/playwright": "4.10.2",
    "playwright": "1.49.2"
  }
}
```

## Automated Accessibility Tests with axe-core

### Example: Page-level Accessibility Scan

```typescript
import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import { axe } from 'axe-core';
import HomePage from '../HomePage.vue';

describe('HomePage Accessibility', () => {
  it('should have no accessibility violations', async () => {
    const wrapper = mount(HomePage);
    
    // Get the root DOM element rendered by Vue Test Utils
    const rootNode = wrapper.element;
    
    // Run axe accessibility scan
    const results = await axe.run(rootNode);
    
    // Assert no violations
    expect(results.violations).toHaveLength(0);
    
    // If violations exist, log them for debugging
    if (results.violations.length > 0) {
      console.error('Accessibility violations:', 
        results.violations.map(v => ({
          id: v.id,
          impact: v.impact,
          description: v.description,
          nodes: v.nodes.length
        }))
      );
    }
  });
});
```

### Example: Component-level Accessibility Test

```typescript
import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import { axe } from 'axe-core';
import ResultsTable from '../components/ResultsTable.vue';

describe('ResultsTable Accessibility', () => {
  const mockData = [
    { position: 1, bib: '101', crew: 'Crew A', time: '05:23.45' },
    { position: 2, bib: '102', crew: 'Crew B', time: '05:25.12' }
  ];

  it('should have accessible table structure', async () => {
    const wrapper = mount(ResultsTable, {
      props: { results: mockData }
    });
    
    const results = await axe.run(wrapper.element, {
      rules: {
        // Focus on table-specific rules
        'table-duplicate-name': { enabled: true },
        'table-fake-caption': { enabled: true },
        'td-headers-attr': { enabled: true },
        'th-has-data-cells': { enabled: true }
      }
    });
    
    expect(results.violations).toHaveLength(0);
  });

  it('should have proper heading hierarchy', async () => {
    const wrapper = mount(ResultsTable, {
      props: { results: mockData }
    });
    
    const results = await axe.run(wrapper.element, {
      rules: {
        'heading-order': { enabled: true }
      }
    });
    
    expect(results.violations).toHaveLength(0);
  });

  it('should meet color contrast requirements (WCAG AA)', async () => {
    const wrapper = mount(ResultsTable, {
      props: { results: mockData }
    });
    
    const results = await axe.run(wrapper.element, {
      rules: {
        'color-contrast': { enabled: true }
      }
    });
    
    expect(results.violations).toHaveLength(0);
  });
});
```

## Manual Accessibility Checklist

For critical flows (public results, operator capture, staff workflows), complete this checklist:

### Keyboard Navigation
- [ ] All interactive elements reachable via Tab key
- [ ] Tab order follows logical reading order
- [ ] Enter/Space activates buttons and links
- [ ] Escape closes modals and dropdowns
- [ ] Arrow keys work in custom widgets (if applicable)
- [ ] Focus indicators clearly visible
- [ ] No keyboard traps

### Screen Reader Testing
- [ ] Page title announces correctly
- [ ] Headings create logical structure
- [ ] Links have descriptive text (not "click here")
- [ ] Images have appropriate alt text
- [ ] Form fields have associated labels
- [ ] Error messages are announced
- [ ] Dynamic content changes announced (via aria-live)
- [ ] Loading states communicated

### Visual Requirements
- [ ] Color contrast ratio ≥4.5:1 for normal text (WCAG AA)
- [ ] Color contrast ratio ≥3:1 for large text (≥18pt or 14pt bold)
- [ ] Information not conveyed by color alone
- [ ] Focus indicators have ≥3:1 contrast
- [ ] Text can be resized to 200% without loss of content

### Touch/Mobile
- [ ] Touch targets ≥44×44 CSS pixels
- [ ] No hover-only interactions
- [ ] Pinch zoom not disabled
- [ ] Orientation changes handled gracefully

### Operator High-Contrast Mode
- [ ] Content visible in high-contrast mode
- [ ] Focus indicators prominent
- [ ] Line-scan markers clearly visible
- [ ] Status indicators distinguishable

## Testing Tools

### Browser DevTools
- **Chrome:** Lighthouse (Accessibility audit)
- **Firefox:** Accessibility Inspector
- **Edge:** Accessibility Insights

### Screen Readers
- **NVDA** (Windows, free)
- **JAWS** (Windows, commercial)
- **VoiceOver** (macOS/iOS, built-in)
- **TalkBack** (Android, built-in)

### Browser Extensions
- **axe DevTools** - Automated testing
- **WAVE** - Visual accessibility checker
- **Accessibility Insights** - Comprehensive testing

## CI Integration

Add to `package.json`:

```json
{
  "scripts": {
    "test:a11y": "vitest run --config vitest.a11y.config.ts",
    "test:a11y:watch": "vitest --config vitest.a11y.config.ts"
  }
}
```

Create `vitest.a11y.config.ts`:

```typescript
import { defineConfig } from 'vitest/config';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./tests/setup.ts'],
    include: ['**/*.a11y.test.ts']
  }
});
```

## References

- [WCAG 2.2 Quick Reference](https://www.w3.org/WAI/WCAG22/quickref/)
- [axe-core Rules](https://github.com/dequelabs/axe-core/blob/develop/doc/rule-descriptions.md)
- [Vue Accessibility Guide](https://vuejs.org/guide/best-practices/accessibility.html)
- [Testing Strategy](../../../../docs/TESTING_STRATEGY.md)

## Reporting Issues

When an accessibility issue is found:
1. Document the violation (rule ID, impact, description)
2. Provide steps to reproduce
3. Suggest remediation
4. Link to WCAG success criterion
5. Assign priority based on impact (critical, serious, moderate, minor)
