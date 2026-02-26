import { beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';

async function importFreshUsePWAInstall() {
  vi.resetModules();
  return import('../composables/usePWAInstall.js');
}

function createMockBeforeInstallPromptEvent() {
  return {
    prompt: vi.fn().mockResolvedValue(undefined),
    userChoice: Promise.resolve({ outcome: 'accepted' }),
    preventDefault: vi.fn(),
  };
}

describe('usePWAInstall initialization', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
  });

  it('detects when PWA is installable', async () => {
    let beforeInstallPromptHandler = null;

    vi.stubGlobal('addEventListener', (event, handler) => {
      if (event === 'beforeinstallprompt') {
        beforeInstallPromptHandler = handler;
      }
    });

    const { usePWAInstall } = await importFreshUsePWAInstall();
    const { isInstallable } = usePWAInstall();

    expect(isInstallable.value).toBe(false);

    // Simulate beforeinstallprompt event
    const mockEvent = createMockBeforeInstallPromptEvent();
    if (beforeInstallPromptHandler) {
      beforeInstallPromptHandler(mockEvent);
    }
    await nextTick();

    expect(isInstallable.value).toBe(true);
    expect(mockEvent.preventDefault).toHaveBeenCalled();
  });

  it('detects when PWA is already installed', async () => {
    vi.stubGlobal('matchMedia', (query) => {
      if (query === '(display-mode: standalone)') {
        return { matches: true };
      }
      return { matches: false };
    });

    const { usePWAInstall } = await importFreshUsePWAInstall();
    const { isInstalled } = usePWAInstall();

    expect(isInstalled.value).toBe(true);
  });

  it('detects when PWA is running in browser', async () => {
    vi.stubGlobal('matchMedia', (query) => {
      return { matches: false };
    });

    const { usePWAInstall } = await importFreshUsePWAInstall();
    const { isInstalled } = usePWAInstall();

    expect(isInstalled.value).toBe(false);
  });

  it('detects iOS standalone mode', async () => {
    vi.stubGlobal('navigator', {
      standalone: true,
    });

    const { usePWAInstall } = await importFreshUsePWAInstall();
    const { isInstalled } = usePWAInstall();

    expect(isInstalled.value).toBe(true);
  });
});

describe('usePWAInstall prompt', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows install prompt when available', async () => {
    let beforeInstallPromptHandler = null;
    const mockEvent = createMockBeforeInstallPromptEvent();

    vi.stubGlobal('addEventListener', (event, handler) => {
      if (event === 'beforeinstallprompt') {
        beforeInstallPromptHandler = handler;
      }
    });

    const { usePWAInstall } = await importFreshUsePWAInstall();
    const { showInstallPrompt } = usePWAInstall();

    // Capture the event
    if (beforeInstallPromptHandler) {
      beforeInstallPromptHandler(mockEvent);
    }
    await nextTick();

    // Show prompt
    const result = await showInstallPrompt();

    expect(mockEvent.prompt).toHaveBeenCalled();
    expect(result).toBe('accepted');
  });

  it('returns null when install prompt not available', async () => {
    const { usePWAInstall } = await importFreshUsePWAInstall();
    const { showInstallPrompt } = usePWAInstall();

    const result = await showInstallPrompt();

    expect(result).toBeNull();
  });

  it('handles user dismissing install prompt', async () => {
    let beforeInstallPromptHandler = null;
    const mockEvent = {
      ...createMockBeforeInstallPromptEvent(),
      userChoice: Promise.resolve({ outcome: 'dismissed' }),
    };

    vi.stubGlobal('addEventListener', (event, handler) => {
      if (event === 'beforeinstallprompt') {
        beforeInstallPromptHandler = handler;
      }
    });

    const { usePWAInstall } = await importFreshUsePWAInstall();
    const { showInstallPrompt } = usePWAInstall();

    if (beforeInstallPromptHandler) {
      beforeInstallPromptHandler(mockEvent);
    }
    await nextTick();

    const result = await showInstallPrompt();

    expect(result).toBe('dismissed');
  });

  it('clears prompt after installation', async () => {
    let beforeInstallPromptHandler = null;
    let appInstalledHandler = null;
    const mockEvent = createMockBeforeInstallPromptEvent();

    vi.stubGlobal('addEventListener', (event, handler) => {
      if (event === 'beforeinstallprompt') {
        beforeInstallPromptHandler = handler;
      }
      if (event === 'appinstalled') {
        appInstalledHandler = handler;
      }
    });

    const { usePWAInstall } = await importFreshUsePWAInstall();
    const { isInstallable, showInstallPrompt } = usePWAInstall();

    if (beforeInstallPromptHandler) {
      beforeInstallPromptHandler(mockEvent);
    }
    await nextTick();

    expect(isInstallable.value).toBe(true);

    await showInstallPrompt();

    // Simulate appinstalled event
    if (appInstalledHandler) {
      appInstalledHandler();
    }
    await nextTick();

    expect(isInstallable.value).toBe(false);
  });
});

describe('usePWAInstall platform detection', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
  });

  it('detects iOS platform', async () => {
    vi.stubGlobal('navigator', {
      userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) AppleWebKit/605.1.15',
      platform: 'iPhone',
    });

    const { usePWAInstall } = await importFreshUsePWAInstall();
    const { platform } = usePWAInstall();

    expect(platform.value).toBe('ios');
  });

  it('detects Android platform', async () => {
    vi.stubGlobal('navigator', {
      userAgent: 'Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36',
      platform: 'Linux',
    });

    const { usePWAInstall } = await importFreshUsePWAInstall();
    const { platform } = usePWAInstall();

    expect(platform.value).toBe('android');
  });

  it('detects desktop platform', async () => {
    vi.stubGlobal('navigator', {
      userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/98.0.4758.102',
      platform: 'Win32',
    });

    const { usePWAInstall } = await importFreshUsePWAInstall();
    const { platform } = usePWAInstall();

    expect(platform.value).toBe('desktop');
  });
});

describe('usePWAInstall canInstall computed', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
  });

  it('returns true when installable and not installed', async () => {
    let beforeInstallPromptHandler = null;

    vi.stubGlobal('addEventListener', (event, handler) => {
      if (event === 'beforeinstallprompt') {
        beforeInstallPromptHandler = handler;
      }
    });

    vi.stubGlobal('matchMedia', () => ({ matches: false }));

    const { usePWAInstall } = await importFreshUsePWAInstall();
    const { canInstall } = usePWAInstall();

    const mockEvent = createMockBeforeInstallPromptEvent();
    if (beforeInstallPromptHandler) {
      beforeInstallPromptHandler(mockEvent);
    }
    await nextTick();

    expect(canInstall.value).toBe(true);
  });

  it('returns false when already installed', async () => {
    vi.stubGlobal('matchMedia', (query) => {
      if (query === '(display-mode: standalone)') {
        return { matches: true };
      }
      return { matches: false };
    });

    const { usePWAInstall } = await importFreshUsePWAInstall();
    const { canInstall } = usePWAInstall();

    expect(canInstall.value).toBe(false);
  });

  it('returns false when not installable', async () => {
    vi.stubGlobal('matchMedia', () => ({ matches: false }));

    const { usePWAInstall } = await importFreshUsePWAInstall();
    const { canInstall } = usePWAInstall();

    expect(canInstall.value).toBe(false);
  });
});

describe('usePWAInstall iOS instructions', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
  });

  it('provides iOS manual install instructions', async () => {
    vi.stubGlobal('navigator', {
      userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X)',
      platform: 'iPhone',
    });

    const { usePWAInstall } = await importFreshUsePWAInstall();
    const { platform, needsManualInstructions } = usePWAInstall();

    expect(platform.value).toBe('ios');
    expect(needsManualInstructions.value).toBe(true);
  });

  it('does not show manual instructions on Android', async () => {
    vi.stubGlobal('navigator', {
      userAgent: 'Mozilla/5.0 (Linux; Android 12)',
      platform: 'Linux',
    });

    const { usePWAInstall } = await importFreshUsePWAInstall();
    const { needsManualInstructions } = usePWAInstall();

    expect(needsManualInstructions.value).toBe(false);
  });
});
