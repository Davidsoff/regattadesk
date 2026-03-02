import fs from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'

const localeFiles = ['en.json', 'nl.json']
const localesDir = path.resolve(import.meta.dirname, '..', 'locales')

describe('i18n locale formatting', () => {
  for (const fileName of localeFiles) {
    it(`${fileName} is valid JSON and canonically formatted`, () => {
      const filePath = path.join(localesDir, fileName)
      const source = fs.readFileSync(filePath, 'utf8')
      const parsed = JSON.parse(source)
      const formatted = `${JSON.stringify(parsed, null, 2)}\n`

      expect(source).toBe(formatted)
    })
  }
})
