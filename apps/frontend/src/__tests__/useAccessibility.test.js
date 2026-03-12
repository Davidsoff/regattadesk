import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useFocusTrap } from '../composables/useFocusTrap'
import { createSkipLink } from '../composables/useSkipLink'
import { useFocusManagement } from '../composables/useFocusManagement'

// ─── DOM helpers ──────────────────────────────────────────────────────────────

function makeButton(id = 'btn') {
  const el = document.createElement('button')
  el.id = id
  el.textContent = id
  document.body.appendChild(el)
  return el
}

function makeInput(id = 'inp') {
  const el = document.createElement('input')
  el.id = id
  document.body.appendChild(el)
  return el
}

function makeDiv(opts = {}) {
  const el = document.createElement('div')
  if (opts.id) el.id = opts.id
  if (opts.role) el.setAttribute('role', opts.role)
  if (opts.className) el.className = opts.className
  document.body.appendChild(el)
  return el
}

function makeHeading(id, level = 1) {
  const el = document.createElement(`h${level}`)
  if (id) el.id = id
  el.textContent = 'Page heading'
  document.body.appendChild(el)
  return el
}

// ─── useFocusTrap ─────────────────────────────────────────────────────────────

describe('useFocusTrap', () => {
  let container
  let btn1
  let btn2

  beforeEach(() => {
    container = document.createElement('div')
    btn1 = document.createElement('button')
    btn1.textContent = 'First'
    btn2 = document.createElement('button')
    btn2.textContent = 'Second'
    container.appendChild(btn1)
    container.appendChild(btn2)
    document.body.appendChild(container)
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.restoreAllMocks()
  })

  it('activate focuses the first focusable element inside the trap', async () => {
    const { trapRef, activate } = useFocusTrap()
    trapRef.value = container

    const focusSpy = vi.spyOn(btn1, 'focus')
    await activate()

    expect(focusSpy).toHaveBeenCalled()
  })

  it('deactivate returns focus to trigger element', async () => {
    const trigger = makeButton('trigger')
    const { trapRef, activate, deactivate } = useFocusTrap()
    trapRef.value = container

    await activate(trigger)

    const focusSpy = vi.spyOn(trigger, 'focus')
    deactivate()

    expect(focusSpy).toHaveBeenCalled()
  })

  it('Tab wraps from last to first element', async () => {
    const { trapRef, activate } = useFocusTrap()
    trapRef.value = container

    await activate()

    // Simulate Tab on last element
    btn2.focus()
    const focusSpy = vi.spyOn(btn1, 'focus')
    const event = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true })
    Object.defineProperty(event, 'shiftKey', { value: false })
    document.dispatchEvent(event)

    expect(focusSpy).toHaveBeenCalled()
  })

  it('Shift+Tab wraps from first to last element', async () => {
    const { trapRef, activate } = useFocusTrap()
    trapRef.value = container

    await activate()

    // Simulate Shift+Tab on first element
    btn1.focus()
    const focusSpy = vi.spyOn(btn2, 'focus')
    const event = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, shiftKey: true })
    document.dispatchEvent(event)

    expect(focusSpy).toHaveBeenCalled()
  })

  it('deactivate removes keydown listener', async () => {
    const { trapRef, activate, deactivate } = useFocusTrap()
    trapRef.value = container

    await activate()
    deactivate()

    // After deactivate, Tab should not wrap focus
    btn2.focus()
    const focusSpy = vi.spyOn(btn1, 'focus')
    const event = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true })
    document.dispatchEvent(event)

    expect(focusSpy).not.toHaveBeenCalled()
  })
})



describe('createSkipLink', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('skipToMain focuses the #main-content element', () => {
    const main = document.createElement('main')
    main.id = 'main-content'
    document.body.appendChild(main)

    const focusSpy = vi.spyOn(main, 'focus')

    const { skipToMain } = createSkipLink()
    skipToMain()

    expect(focusSpy).toHaveBeenCalled()
  })

  it('skipToMain does nothing when element does not exist', () => {
    // No main-content in DOM
    const { skipToMain } = createSkipLink()
    // Should not throw
    expect(() => skipToMain()).not.toThrow()
  })

  it('skipToMain accepts a custom id', () => {
    const custom = document.createElement('div')
    custom.id = 'custom-main'
    document.body.appendChild(custom)

    const focusSpy = vi.spyOn(custom, 'focus')

    const { skipToMain } = createSkipLink()
    skipToMain('custom-main')

    expect(focusSpy).toHaveBeenCalled()
  })
})

// ─── useFocusManagement ───────────────────────────────────────────────────────

describe('useFocusManagement', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('focusPageHeading focuses the first h1', async () => {
    const h1 = makeHeading(undefined, 1)
    const focusSpy = vi.spyOn(h1, 'focus')

    const { focusPageHeading } = useFocusManagement()
    await focusPageHeading()

    expect(focusSpy).toHaveBeenCalled()
  })

  it('focusPageHeading accepts a custom selector', async () => {
    const h2 = makeHeading('section-heading', 2)
    const focusSpy = vi.spyOn(h2, 'focus')

    const { focusPageHeading } = useFocusManagement()
    await focusPageHeading('h2')

    expect(focusSpy).toHaveBeenCalled()
  })

  it('focusPageHeading does nothing when selector matches nothing', async () => {
    const { focusPageHeading } = useFocusManagement()
    // Should not throw
    await expect(focusPageHeading('.nonexistent')).resolves.toBeUndefined()
  })

  it('focusFirstError focuses the first [role=alert] element', async () => {
    const alertEl = makeDiv({ role: 'alert' })
    const focusSpy = vi.spyOn(alertEl, 'focus')

    const { focusFirstError } = useFocusManagement()
    await focusFirstError()

    expect(focusSpy).toHaveBeenCalled()
  })

  it('focusFirstError accepts a custom selector', async () => {
    const errDiv = makeDiv({ className: 'error-summary' })
    const focusSpy = vi.spyOn(errDiv, 'focus')

    const { focusFirstError } = useFocusManagement()
    await focusFirstError('.error-summary')

    expect(focusSpy).toHaveBeenCalled()
  })

  it('focusFirstError does nothing when selector matches nothing', async () => {
    const { focusFirstError } = useFocusManagement()
    await expect(focusFirstError('.nonexistent')).resolves.toBeUndefined()
  })
})
