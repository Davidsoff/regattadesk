import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { execFileSync } from 'node:child_process'

const projectRoot = path.resolve(import.meta.dirname, '..')
const reportPath = path.join(os.tmpdir(), `vue-i18n-extract-report-${Date.now()}.json`)
const baselinePath = path.join(projectRoot, 'i18n-unused-keys-baseline.json')

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, 'utf8'))
}

try {
  const reportOutput = execFileSync(
    'npx',
    [
      'vue-i18n-extract',
      'report',
      '--vueFiles',
      'src/**/*.{vue,js}',
      '--languageFiles',
      'src/i18n/locales/*.json',
      '--output',
      reportPath
    ],
    {
      cwd: projectRoot,
      encoding: 'utf8'
    }
  )

  const report = readJson(reportPath)
  const baseline = new Set(readJson(baselinePath))

  const missingKeys = report.missingKeys ?? []
  const unexpectedUnused = (report.unusedKeys ?? []).filter((item) => !baseline.has(item.path))

  if (missingKeys.length > 0 || unexpectedUnused.length > 0) {
    if (reportOutput.trim()) {
      console.error(reportOutput)
    }

    if (missingKeys.length > 0) {
      console.error('\nMissing translation keys:')
      for (const item of missingKeys) {
        console.error(`- ${item.path} (${item.language}) in ${item.file}`)
      }
    }

    if (unexpectedUnused.length > 0) {
      console.error('\nUnexpected unused translation keys:')
      for (const item of unexpectedUnused) {
        console.error(`- ${item.path} (${item.language}) in ${item.file}`)
      }
      console.error('\nIf intentional, update i18n-unused-keys-baseline.json.')
    }

    process.exit(1)
  }

  console.log(
    `\nI18n check passed (${missingKeys.length} missing, ${unexpectedUnused.length} unexpected unused).`
  )
} catch (error) {
  if (error.stdout) {
    process.stderr.write(error.stdout)
  }
  if (error.stderr) {
    process.stderr.write(error.stderr)
  }
  throw error
} finally {
  if (fs.existsSync(reportPath)) {
    fs.rmSync(reportPath, { force: true })
  }
}
