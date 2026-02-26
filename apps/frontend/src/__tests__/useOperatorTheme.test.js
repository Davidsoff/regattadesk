import { beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';

async function importFreshUseOperatorTheme() {
  vi.resetModules();
  return import('../composables/useOperatorTheme.js');
}

function stubStorage(storedContrast = null, storedDensity = null) {
  const storage = new Map();
  if (storedContrast !== null) {
    storage.set('regattadesk-operator-contrast', storedContrast);
  }
  if (storedDensity !== null) {
    storage.set('regattadesk-operator-density', storedDensity);
  }

  vi.stubGlobal('localStorage', {
    getItem: (key) => storage.get(key) || null,
    setItem: (key, value) => storage.set(key, value),
    removeItem: (key) => storage.delete(key),
  });

  return storage;
}

describe('useOperatorTheme initialization', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
    document.documentElement.removeAttribute('data-contrast');
    document.documentElement.removeAttribute('data-density');
  });

  it('defaults to high-contrast mode for operators', async () => {
    stubStorage();

    const { useOperatorTheme } = await importFreshUseOperatorTheme();
    const { contrast } = useOperatorTheme();

    expect(contrast.value).toBe('high');
    expect(document.documentElement.getAttribute('data-contrast')).toBe('high');
  });

  it('restores persisted contrast mode', async () => {
    stubStorage('standard');

    const { useOperatorTheme } = await importFreshUseOperatorTheme();
    const { contrast } = useOperatorTheme();

    expect(contrast.value).toBe('standard');
    expect(document.documentElement.getAttribute('data-contrast')).toBe('standard');
  });

  it('defaults to comfortable density', async () => {
    stubStorage();

    const { useOperatorTheme } = await importFreshUseOperatorTheme();
    const { density } = useOperatorTheme();

    expect(density.value).toBe('comfortable');
    expect(document.documentElement.getAttribute('data-density')).toBe('comfortable');
  });

  it('restores persisted density mode', async () => {
    stubStorage(null, 'compact');

    const { useOperatorTheme } = await importFreshUseOperatorTheme();
    const { density } = useOperatorTheme();

    expect(density.value).toBe('compact');
    expect(document.documentElement.getAttribute('data-density')).toBe('compact');
  });

  it('handles storage access errors gracefully', async () => {
    vi.stubGlobal('localStorage', {
      getItem: () => {
        throw new Error('Storage unavailable');
      },
      setItem: vi.fn(),
    });

    const { useOperatorTheme } = await importFreshUseOperatorTheme();
    const { contrast } = useOperatorTheme();

    // Should fall back to default high-contrast
    expect(contrast.value).toBe('high');
  });
});

describe('useOperatorTheme setContrast', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
    document.documentElement.removeAttribute('data-contrast');
  });

  it('updates contrast mode and persists to storage', async () => {
    const storage = stubStorage();

    const { useOperatorTheme } = await importFreshUseOperatorTheme();
    const { contrast, setContrast } = useOperatorTheme();

    setContrast('standard');
    await nextTick();

    expect(contrast.value).toBe('standard');
    expect(document.documentElement.getAttribute('data-contrast')).toBe('standard');
    expect(storage.get('regattadesk-operator-contrast')).toBe('standard');
  });

  it('updates DOM attribute immediately', async () => {
    stubStorage();

    const { useOperatorTheme } = await importFreshUseOperatorTheme();
    const { setContrast } = useOperatorTheme();

    setContrast('high');
    
    // Should be synchronous DOM update
    expect(document.documentElement.getAttribute('data-contrast')).toBe('high');
  });

  it('validates contrast values', async () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    stubStorage();

    const { useOperatorTheme } = await importFreshUseOperatorTheme();
    const { contrast, setContrast } = useOperatorTheme();

    setContrast('invalid-value');
    await nextTick();

    // Should stay at default value
    expect(contrast.value).toBe('high');
    expect(warnSpy).toHaveBeenCalled();
    
    warnSpy.mockRestore();
  });

  it('handles storage write failures gracefully', async () => {
    vi.stubGlobal('localStorage', {
      getItem: () => null,
      setItem: () => {
        throw new Error('Storage write failed');
      },
    });

    const { useOperatorTheme } = await importFreshUseOperatorTheme();
    const { contrast, setContrast } = useOperatorTheme();

    setContrast('standard');
    await nextTick();

    // Should still update runtime state even if persistence fails
    expect(contrast.value).toBe('standard');
    expect(document.documentElement.getAttribute('data-contrast')).toBe('standard');
  });
});

describe('useOperatorTheme setDensity', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
    document.documentElement.removeAttribute('data-density');
  });

  it('updates density mode and persists to storage', async () => {
    const storage = stubStorage();

    const { useOperatorTheme } = await importFreshUseOperatorTheme();
    const { density, setDensity } = useOperatorTheme();

    setDensity('compact');
    await nextTick();

    expect(density.value).toBe('compact');
    expect(document.documentElement.getAttribute('data-density')).toBe('compact');
    expect(storage.get('regattadesk-operator-density')).toBe('compact');
  });

  it('supports all density values', async () => {
    const storage = stubStorage();

    const { useOperatorTheme } = await importFreshUseOperatorTheme();
    const { density, setDensity } = useOperatorTheme();

    for (const value of ['comfortable', 'compact', 'spacious']) {
      setDensity(value);
      await nextTick();
      
      expect(density.value).toBe(value);
      expect(document.documentElement.getAttribute('data-density')).toBe(value);
      expect(storage.get('regattadesk-operator-density')).toBe(value);
    }
  });

  it('validates density values', async () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    stubStorage();

    const { useOperatorTheme } = await importFreshUseOperatorTheme();
    const { density, setDensity } = useOperatorTheme();

    setDensity('mega-dense');
    await nextTick();

    // Should stay at default value
    expect(density.value).toBe('comfortable');
    expect(warnSpy).toHaveBeenCalled();
    
    warnSpy.mockRestore();
  });
});

describe('useOperatorTheme toggleContrast', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
    document.documentElement.removeAttribute('data-contrast');
  });

  it('toggles between high and standard contrast', async () => {
    const storage = stubStorage();

    const { useOperatorTheme } = await importFreshUseOperatorTheme();
    const { contrast, toggleContrast } = useOperatorTheme();

    // Starts at high (default)
    expect(contrast.value).toBe('high');

    toggleContrast();
    await nextTick();
    expect(contrast.value).toBe('standard');
    expect(storage.get('regattadesk-operator-contrast')).toBe('standard');

    toggleContrast();
    await nextTick();
    expect(contrast.value).toBe('high');
    expect(storage.get('regattadesk-operator-contrast')).toBe('high');
  });
});

describe('useOperatorTheme isHighContrast computed', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
    document.documentElement.removeAttribute('data-contrast');
  });

  it('returns true when contrast is high', async () => {
    stubStorage('high');

    const { useOperatorTheme } = await importFreshUseOperatorTheme();
    const { isHighContrast } = useOperatorTheme();

    expect(isHighContrast.value).toBe(true);
  });

  it('returns false when contrast is standard', async () => {
    stubStorage('standard');

    const { useOperatorTheme } = await importFreshUseOperatorTheme();
    const { isHighContrast } = useOperatorTheme();

    expect(isHighContrast.value).toBe(false);
  });

  it('updates reactively with contrast changes', async () => {
    stubStorage();

    const { useOperatorTheme } = await importFreshUseOperatorTheme();
    const { isHighContrast, setContrast } = useOperatorTheme();

    expect(isHighContrast.value).toBe(true);

    setContrast('standard');
    await nextTick();
    expect(isHighContrast.value).toBe(false);

    setContrast('high');
    await nextTick();
    expect(isHighContrast.value).toBe(true);
  });
});

describe('useOperatorTheme per-device persistence', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
    document.documentElement.removeAttribute('data-contrast');
    document.documentElement.removeAttribute('data-density');
  });

  it('uses separate storage keys for operator theme', async () => {
    const storage = stubStorage();

    const { useOperatorTheme } = await importFreshUseOperatorTheme();
    const { setContrast, setDensity } = useOperatorTheme();

    setContrast('standard');
    setDensity('compact');
    await nextTick();

    // Verify operator-specific keys are used
    expect(storage.has('regattadesk-operator-contrast')).toBe(true);
    expect(storage.has('regattadesk-operator-density')).toBe(true);
    expect(storage.get('regattadesk-operator-contrast')).toBe('standard');
    expect(storage.get('regattadesk-operator-density')).toBe('compact');
  });

  it('persists independently from staff/public theme', async () => {
    const storage = stubStorage();
    // Simulate staff theme preferences
    storage.set('regattadesk-contrast', 'standard');
    storage.set('regattadesk-density', 'comfortable');

    const { useOperatorTheme } = await importFreshUseOperatorTheme();
    const { contrast, density } = useOperatorTheme();

    // Operator should have independent defaults
    expect(contrast.value).toBe('high');
    expect(density.value).toBe('comfortable');

    // Staff preferences should remain unchanged
    expect(storage.get('regattadesk-contrast')).toBe('standard');
  });
});
