import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

import i18n from '../i18n'
import OperatorAccess from '../views/staff/OperatorAccess.vue'

const REGATTA_ID = 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc'
const TOKEN_ID = '0d7e2de8-084a-4e33-85a4-5400066cc23e'
const HANDOFF_ID = 'b8082c46-b8ca-43af-88ee-c9e5605522bb'

const mockStaffOperatorAccessApi = {
  listTokens: vi.fn(),
  createToken: vi.fn(),
  revokeToken: vi.fn(),
  exportTokenPdf: vi.fn(),
  listPendingHandoffs: vi.fn(),
  getStationHandoff: vi.fn(),
  adminRevealPin: vi.fn(),
}

vi.mock('../api', () => ({
  createApiClient: vi.fn(() => ({})),
  createStaffOperatorAccessApi: vi.fn(() => mockStaffOperatorAccessApi),
}))

function createToken(overrides = {}) {
  return {
    id: TOKEN_ID,
    regatta_id: REGATTA_ID,
    block_id: '5af6a94e-1e9d-4234-9d60-8c1931d1a3b5',
    station: 'Finish Tower',
    valid_from: '2026-03-10T08:00:00Z',
    valid_until: '2026-03-10T18:00:00Z',
    is_active: true,
    ...overrides,
  }
}

function createHandoff(overrides = {}) {
  return {
    id: HANDOFF_ID,
    regatta_id: REGATTA_ID,
    token_id: TOKEN_ID,
    station: 'Finish Tower',
    requestingDeviceId: 'pixel-operator-2',
    status: 'PENDING',
    createdAt: '2026-03-10T08:45:00Z',
    expiresAt: '2026-03-10T08:50:00Z',
    completedAt: null,
    pin: null,
    ...overrides,
  }
}

async function mountPage(options = {}) {
  const { userRole = 'regatta_admin' } = options

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/staff/regattas/:regattaId/operator-access',
        name: 'staff-regatta-operator-access',
        component: OperatorAccess,
      },
    ],
  })

  await router.push(`/staff/regattas/${REGATTA_ID}/operator-access`)
  await router.isReady()

  globalThis.__REGATTADESK_AUTH__ = {
    staffAuthenticated: true,
    user: {
      role: userRole,
    },
  }

  return mount(OperatorAccess, {
    attachTo: document.body,
    global: {
      plugins: [router, i18n],
    },
  })
}

describe('OperatorAccess view (issue #137)', () => {
  let createObjectUrlSpy
  let revokeObjectUrlSpy
  let anchorClickSpy

  beforeEach(() => {
    vi.clearAllMocks()
    document.body.innerHTML = ''
    createObjectUrlSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:download-token')
    revokeObjectUrlSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
    anchorClickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    mockStaffOperatorAccessApi.listTokens.mockResolvedValue({
      data: [createToken()],
    })
    mockStaffOperatorAccessApi.listPendingHandoffs.mockResolvedValue([createHandoff()])
    mockStaffOperatorAccessApi.createToken.mockResolvedValue(
      createToken({
        id: '9df6f4a2-f053-4329-b89e-6c0b61896f4a',
        station: 'Start Pontoon',
      })
    )
    mockStaffOperatorAccessApi.revokeToken.mockResolvedValue({ message: 'Token revoked successfully' })
    mockStaffOperatorAccessApi.exportTokenPdf.mockResolvedValue({
      blob: new Blob(['pdf-content'], { type: 'application/pdf' }),
      filename: 'operator-token-finish-tower.pdf',
      contentType: 'application/pdf',
      size: 4096,
    })
    mockStaffOperatorAccessApi.adminRevealPin.mockResolvedValue({
      ...createHandoff(),
      pin: '4821',
    })
  })

  afterEach(() => {
    document.body.innerHTML = ''
    delete globalThis.__REGATTADESK_AUTH__
    createObjectUrlSpy.mockRestore()
    revokeObjectUrlSpy.mockRestore()
    anchorClickSpy.mockRestore()
  })

  it('loads token administration data and renders token lifecycle actions with fallback instructions', async () => {
    const wrapper = await mountPage()

    await vi.waitFor(() => {
      expect(mockStaffOperatorAccessApi.listTokens).toHaveBeenCalledWith(REGATTA_ID)
    })
    await vi.waitFor(() => {
      expect(mockStaffOperatorAccessApi.listPendingHandoffs).toHaveBeenCalledWith(REGATTA_ID, {
        station: '',
        token_id: '',
      })
    })
    await vi.waitFor(() => {
      expect(wrapper.find(`[data-testid="token-row-${TOKEN_ID}"]`).exists()).toBe(true)
    })

    expect(wrapper.text()).toContain('Operator access')
    expect(wrapper.text()).toContain('Finish Tower')
    expect(wrapper.text()).toContain('Valid')
    expect(wrapper.text()).toContain('Fallback')
    expect(wrapper.find('[data-testid="create-token-form"]').exists()).toBe(true)
    expect(wrapper.find(`[data-testid="token-row-${TOKEN_ID}"]`).exists()).toBe(true)
    expect(wrapper.find(`[data-testid="export-token-${TOKEN_ID}"]`).exists()).toBe(true)
    expect(wrapper.find(`[data-testid="revoke-token-${TOKEN_ID}"]`).exists()).toBe(true)
    expect(wrapper.find(`[data-testid="pending-handoff-row-${HANDOFF_ID}"]`).exists()).toBe(true)
  })

  it('creates a token from the staff form and refreshes the administration table', async () => {
    const wrapper = await mountPage()

    await vi.waitFor(() => {
      expect(mockStaffOperatorAccessApi.listTokens).toHaveBeenCalled()
    })

    await wrapper.find('input[name="station"]').setValue('Start Pontoon')
    await wrapper.find('input[name="block_id"]').setValue('9b730f45-b72d-4ac8-9ee0-8df0eaaf5ddb')
    await wrapper.find('input[name="valid_from"]').setValue('2026-03-10T09:00')
    await wrapper.find('input[name="valid_until"]').setValue('2026-03-10T17:00')
    await wrapper.find('[data-testid="create-token-form"]').trigger('submit.prevent')

    await vi.waitFor(() => {
      expect(mockStaffOperatorAccessApi.createToken).toHaveBeenCalledWith(
        REGATTA_ID,
        expect.objectContaining({
          station: 'Start Pontoon',
          block_id: '9b730f45-b72d-4ac8-9ee0-8df0eaaf5ddb',
        })
      )
    })
  })

  it('blocks token creation when valid until is earlier than valid from', async () => {
    const wrapper = await mountPage()

    await vi.waitFor(() => {
      expect(mockStaffOperatorAccessApi.listTokens).toHaveBeenCalled()
    })

    await wrapper.find('input[name="station"]').setValue('Start Pontoon')
    await wrapper.find('input[name="valid_from"]').setValue('2026-03-10T17:00')
    await wrapper.find('input[name="valid_until"]').setValue('2026-03-10T09:00')
    await wrapper.find('[data-testid="create-token-form"]').trigger('submit.prevent')

    expect(mockStaffOperatorAccessApi.createToken).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Valid until must be after valid from.')
  })

  it('requires confirmation before exporting or revoking a token', async () => {
    const wrapper = await mountPage()

    await vi.waitFor(() => {
      expect(wrapper.find(`[data-testid="token-row-${TOKEN_ID}"]`).exists()).toBe(true)
    })

    await wrapper.find(`[data-testid="export-token-${TOKEN_ID}"]`).trigger('click')

    expect(wrapper.find('[data-testid="token-action-confirmation"]').text()).toContain('Export PDF')
    expect(mockStaffOperatorAccessApi.exportTokenPdf).not.toHaveBeenCalled()

    await wrapper.find('[data-testid="cancel-token-action"]').trigger('click')

    expect(wrapper.find('[data-testid="token-action-confirmation"]').exists()).toBe(false)

    await wrapper.find(`[data-testid="export-token-${TOKEN_ID}"]`).trigger('click')

    await wrapper.find('[data-testid="confirm-token-action"]').trigger('click')

    await vi.waitFor(() => {
      expect(mockStaffOperatorAccessApi.exportTokenPdf).toHaveBeenCalledWith(REGATTA_ID, TOKEN_ID)
    })
    expect(createObjectUrlSpy).toHaveBeenCalledTimes(1)
    expect(anchorClickSpy).toHaveBeenCalledTimes(1)
    expect(revokeObjectUrlSpy).toHaveBeenCalledWith('blob:download-token')
    expect(wrapper.text()).toContain('operator-token-finish-tower.pdf')

    await wrapper.find(`[data-testid="revoke-token-${TOKEN_ID}"]`).trigger('click')

    expect(wrapper.find('[data-testid="token-action-confirmation"]').text()).toContain('Revoke')
    expect(mockStaffOperatorAccessApi.revokeToken).not.toHaveBeenCalled()

    await wrapper.find('[data-testid="confirm-token-action"]').trigger('click')

    await vi.waitFor(() => {
      expect(mockStaffOperatorAccessApi.revokeToken).toHaveBeenCalledWith(REGATTA_ID, TOKEN_ID)
    })
  })

  it('loads handoff oversight on mount, supports filters, and shows the admin reveal affordance for regatta admins', async () => {
    const wrapper = await mountPage()

    await vi.waitFor(() => {
      expect(mockStaffOperatorAccessApi.listPendingHandoffs).toHaveBeenCalled()
    })

    await wrapper.find('input[name="handoff_station"]').setValue('Finish Tower')
    await wrapper.find('input[name="handoff_token_id"]').setValue(TOKEN_ID)
    await wrapper.find('[data-testid="load-pending-handoffs"]').trigger('click')

    await vi.waitFor(() => {
      expect(mockStaffOperatorAccessApi.listPendingHandoffs).toHaveBeenLastCalledWith(REGATTA_ID, {
        station: 'Finish Tower',
        token_id: TOKEN_ID,
      })
    })

    const revealButton = wrapper.find(`[data-testid="admin-reveal-pin-${HANDOFF_ID}"]`)
    expect(revealButton.exists()).toBe(true)
    expect(wrapper.text()).toContain('pixel-operator-2')
    expect(wrapper.text()).toContain('2026-03-10 08:50')

    await revealButton.trigger('click')

    await vi.waitFor(() => {
      expect(mockStaffOperatorAccessApi.adminRevealPin).toHaveBeenCalledWith(REGATTA_ID, HANDOFF_ID)
    })

    expect(wrapper.find(`[data-testid="handoff-pin-${HANDOFF_ID}"]`).text()).toContain('4821')
  })

  it('shows an empty state when no pending handoffs are available', async () => {
    mockStaffOperatorAccessApi.listPendingHandoffs.mockResolvedValueOnce([])

    const wrapper = await mountPage()

    await vi.waitFor(() => {
      expect(mockStaffOperatorAccessApi.listPendingHandoffs).toHaveBeenCalled()
    })

    await vi.waitFor(() => {
      expect(wrapper.find('[data-testid="handoff-empty-state"]').exists()).toBe(true)
    })

    expect(wrapper.find('[data-testid="handoff-empty-state"]').text()).toContain('No pending handoffs')
  })

  it('hides privileged actions and shows a clear authorization message for non-admin staff roles', async () => {
    const wrapper = await mountPage({ userRole: 'info_desk' })

    expect(wrapper.find('[data-testid="operator-access-authorization"]').text()).toContain('not authorized')
    expect(wrapper.find('[data-testid="create-token-form"]').exists()).toBe(false)
    expect(wrapper.find(`[data-testid="export-token-${TOKEN_ID}"]`).exists()).toBe(false)
    expect(wrapper.find(`[data-testid="revoke-token-${TOKEN_ID}"]`).exists()).toBe(false)
    expect(wrapper.find(`[data-testid="admin-reveal-pin-${HANDOFF_ID}"]`).exists()).toBe(false)
    expect(mockStaffOperatorAccessApi.listTokens).not.toHaveBeenCalled()
    expect(mockStaffOperatorAccessApi.listPendingHandoffs).not.toHaveBeenCalled()
  })
})
