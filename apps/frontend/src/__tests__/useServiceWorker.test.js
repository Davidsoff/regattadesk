import { beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';

// Mock service worker registration
function createMockRegistration(state = 'activated') {
  return {
    installing: state === 'installing' ? { state: 'installing', addEventListener: vi.fn() } : null,
    waiting: state === 'waiting' ? { state: 'waiting', addEventListener: vi.fn() } : null,
    active: state === 'activated' ? { state: 'activated', addEventListener: vi.fn() } : null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    update: vi.fn(),
  };
}

function createMockServiceWorker() {
  const listeners = new Map();
  return {
    state: 'activated',
    addEventListener: vi.fn((event, handler) => {
      if (!listeners.has(event)) {
        listeners.set(event, []);
      }
      listeners.get(event).push(handler);
    }),
    removeEventListener: vi.fn(),
    _trigger: (event) => {
      const handlers = listeners.get(event) || [];
      handlers.forEach(h => h());
    },
  };
}

async function importFreshUseServiceWorker() {
  vi.resetModules();
  return import('../composables/useServiceWorker.js');
}

describe('useServiceWorker registration', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
  });

  it('registers service worker when available', async () => {
    const mockRegistration = createMockRegistration();
    const registerMock = vi.fn().mockResolvedValue(mockRegistration);

    vi.stubGlobal('navigator', {
      serviceWorker: {
        register: registerMock,
        ready: Promise.resolve(mockRegistration),
      },
    });

    const { useServiceWorker } = await importFreshUseServiceWorker();
    const { register, isRegistered, registration } = useServiceWorker();

    await register('/sw.js');
    await nextTick();

    expect(registerMock).toHaveBeenCalledWith('/sw.js', expect.any(Object));
    expect(isRegistered.value).toBe(true);
    expect(registration.value).toStrictEqual(mockRegistration);
  });

  it('handles registration failure gracefully', async () => {
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    const registerMock = vi.fn().mockRejectedValue(new Error('Registration failed'));

    vi.stubGlobal('navigator', {
      serviceWorker: {
        register: registerMock,
      },
    });

    const { useServiceWorker } = await importFreshUseServiceWorker();
    const { register, isRegistered, error } = useServiceWorker();

    await expect(register('/sw.js')).rejects.toThrow('Registration failed');
    await nextTick();

    expect(isRegistered.value).toBe(false);
    expect(error.value).toBeInstanceOf(Error);
    expect(error.value.message).toBe('Registration failed');
    errorSpy.mockRestore();
  });

  it('does nothing when service worker is not supported', async () => {
    vi.stubGlobal('navigator', {});

    const { useServiceWorker } = await importFreshUseServiceWorker();
    const { register, isRegistered, isSupported } = useServiceWorker();

    await register('/sw.js');
    await nextTick();

    expect(isSupported.value).toBe(false);
    expect(isRegistered.value).toBe(false);
  });

  it('detects service worker state changes', async () => {
    const mockSW = createMockServiceWorker();
    mockSW.state = 'installing';
    const mockRegistration = createMockRegistration('installing');
    mockRegistration.installing = mockSW;

    const registerMock = vi.fn().mockResolvedValue(mockRegistration);

    vi.stubGlobal('navigator', {
      serviceWorker: {
        register: registerMock,
        ready: Promise.resolve(mockRegistration),
      },
    });

    const { useServiceWorker } = await importFreshUseServiceWorker();
    const { register, state } = useServiceWorker();

    await register('/sw.js');
    await nextTick();

    expect(state.value).toBe('installing');

    // Simulate state change to activated
    mockSW.state = 'activated';
    mockSW._trigger('statechange');
    await nextTick();

    expect(state.value).toBe('activated');
  });

  it('triggers update on service worker', async () => {
    const mockRegistration = createMockRegistration();
    mockRegistration.update = vi.fn().mockResolvedValue(mockRegistration);

    vi.stubGlobal('navigator', {
      serviceWorker: {
        register: vi.fn().mockResolvedValue(mockRegistration),
        ready: Promise.resolve(mockRegistration),
      },
    });

    const { useServiceWorker } = await importFreshUseServiceWorker();
    const { register, update } = useServiceWorker();

    await register('/sw.js');
    await update();

    expect(mockRegistration.update).toHaveBeenCalled();
  });

  it('handles multiple registrations idempotently', async () => {
    const mockRegistration = createMockRegistration();
    const registerMock = vi.fn().mockResolvedValue(mockRegistration);

    vi.stubGlobal('navigator', {
      serviceWorker: {
        register: registerMock,
        ready: Promise.resolve(mockRegistration),
      },
    });

    const { useServiceWorker } = await importFreshUseServiceWorker();
    const { register, isRegistered } = useServiceWorker();

    await register('/sw.js');
    await register('/sw.js');
    await nextTick();

    expect(registerMock).toHaveBeenCalledTimes(1);
    expect(isRegistered.value).toBe(true);
  });
});

describe('useServiceWorker message handling', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
  });

  it('sends messages to active service worker', async () => {
    const mockSW = {
      state: 'activated',
      postMessage: vi.fn(),
    };
    const mockRegistration = createMockRegistration();
    mockRegistration.active = mockSW;

    vi.stubGlobal('navigator', {
      serviceWorker: {
        register: vi.fn().mockResolvedValue(mockRegistration),
        ready: Promise.resolve(mockRegistration),
      },
    });

    const { useServiceWorker } = await importFreshUseServiceWorker();
    const { register, sendMessage } = useServiceWorker();

    await register('/sw.js');
    sendMessage({ type: 'SYNC_QUEUE' });

    expect(mockSW.postMessage).toHaveBeenCalledWith({ type: 'SYNC_QUEUE' });
  });

  it('receives messages from service worker', async () => {
    const mockRegistration = createMockRegistration();
    let messageHandler = null;

    vi.stubGlobal('navigator', {
      serviceWorker: {
        register: vi.fn().mockResolvedValue(mockRegistration),
        ready: Promise.resolve(mockRegistration),
        addEventListener: (event, handler) => {
          if (event === 'message') {
            messageHandler = handler;
          }
        },
      },
    });

    const { useServiceWorker } = await importFreshUseServiceWorker();
    const { register, onMessage } = useServiceWorker();

    const receivedMessages = [];
    onMessage((message) => {
      receivedMessages.push(message);
    });

    await register('/sw.js');

    // Simulate message from service worker
    if (messageHandler) {
      messageHandler({ data: { type: 'SYNC_COMPLETE', payload: { count: 5 } } });
    }

    await nextTick();

    expect(receivedMessages).toHaveLength(1);
    expect(receivedMessages[0]).toEqual({ type: 'SYNC_COMPLETE', payload: { count: 5 } });
  });
});
