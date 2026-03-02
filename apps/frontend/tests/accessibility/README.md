# Frontend Accessibility Testing

This directory contains automated accessibility tests for RegattaDesk components and pages.

## Overview

RegattaDesk uses **axe-core** to enforce WCAG 2.2 AA compliance for public pages and critical staff/operator flows.

## Running Tests

```bash
# Run accessibility tests once
npm run test:a11y

# Run in watch mode
npm run test:a11y:watch
```

## Test Coverage

### Primitive Components
- **RdTable**: Table structure, captions, empty states, loading states
- **RdChip**: Color contrast, all variants, dismissible buttons

### Public Pages (WCAG 2.2 AA Required)
- **Schedule**: Heading hierarchy, error messages, critical/serious violations
- **Results**: Live status indicators, SSE connection state

### Critical Flows
- **Public Bootstrap**: `/versions` 401 → `/public/session` → retry flow
- **SSE Connection**: Connection state management, reconnection backoff
- **Operator Offline**: Action queuing, sync protocol, conflict resolution (LWW)
- **Versioned URLs**: Correct construction of `/public/v{draw}-{results}/` paths

## CI Integration

Accessibility tests are **required** to pass in CI. The tests run on every PR and gate merges if critical or serious WCAG violations are detected.

See `.github/workflows/ci.yml` for the CI configuration.

## Test Reports

Test results are saved to `a11y-reports/test-results.json` and uploaded as artifacts in CI.

## Writing New Tests

### Component Accessibility Test Example

```javascript
import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import { run as axeRun } from 'axe-core';
import MyComponent from '../../src/components/MyComponent.vue';

describe('MyComponent Accessibility', () => {
  it('should have no accessibility violations', async () => {
    const wrapper = mount(MyComponent, {
      props: { /* props */ },
      attachTo: document.body  // Required for axe-core
    });

    const results = await axeRun(wrapper.element, {
      rules: {
        'color-contrast': { enabled: true },
        'button-name': { enabled: true }
      }
    });

    expect(results.violations).toHaveLength(0);
    
    wrapper.unmount();  // Clean up
  });
});
```

### Public Page Test Example

For public pages, filter to only block on **critical** and **serious** violations:

```javascript
const results = await axeRun(wrapper.element);

const blockingViolations = results.violations.filter(
  v => v.impact === 'critical' || v.impact === 'serious'
);

expect(blockingViolations).toHaveLength(0);
```

## Manual Testing Checklist

For critical flows (public results, operator capture, staff workflows), complete this checklist before release:

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

## References

- [WCAG 2.2 Quick Reference](https://www.w3.org/WAI/WCAG22/quickref/)
- [axe-core Rules](https://github.com/dequelabs/axe-core/blob/develop/doc/rule-descriptions.md)
- [Vue Accessibility Guide](https://vuejs.org/guide/best-practices/accessibility.html)
- [RegattaDesk Style Guide](../../pdd/design/style-guide.md)

## Reporting Issues

When an accessibility issue is found:
1. Document the violation (rule ID, impact, description)
2. Provide steps to reproduce
3. Suggest remediation
4. Link to WCAG success criterion
5. Assign priority based on impact (critical, serious, moderate, minor)

