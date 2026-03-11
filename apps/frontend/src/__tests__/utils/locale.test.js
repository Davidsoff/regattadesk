import { describe, expect, it } from 'vitest'

import { normalizeLocale } from '../../utils/locale'

describe('normalizeLocale', () => {
  it('returns nl for nl', () => {
    expect(normalizeLocale('nl')).toBe('nl')
  })

  it('returns nl for nl-NL', () => {
    expect(normalizeLocale('nl-NL')).toBe('nl')
  })

  it('returns nl for NL (uppercase)', () => {
    expect(normalizeLocale('NL')).toBe('nl')
  })

  it('returns en for en', () => {
    expect(normalizeLocale('en')).toBe('en')
  })

  it('returns en for en-US', () => {
    expect(normalizeLocale('en-US')).toBe('en')
  })

  it('returns en for EN (uppercase)', () => {
    expect(normalizeLocale('EN')).toBe('en')
  })

  it('returns null for unsupported locale', () => {
    expect(normalizeLocale('fr')).toBeNull()
  })

  it('returns null for empty string', () => {
    expect(normalizeLocale('')).toBeNull()
  })

  it('returns null for whitespace-only string', () => {
    expect(normalizeLocale('   ')).toBeNull()
  })

  it('returns null for non-string values', () => {
    expect(normalizeLocale(null)).toBeNull()
    expect(normalizeLocale(undefined)).toBeNull()
    expect(normalizeLocale(42)).toBeNull()
    expect(normalizeLocale({})).toBeNull()
  })
})
