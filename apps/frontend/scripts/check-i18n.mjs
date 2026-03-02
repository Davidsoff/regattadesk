import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { execFileSync } from 'node:child_process'

const projectRoot = path.resolve(import.meta.dirname, '..')
const reportPath = path.join(os.tmpdir(), `vue-i18n-extract-report-${Date.now()}.json`)

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

  const missingKeys = report.missingKeys ?? []
  const unusedKeys = report.unusedKeys ?? []

  if (missingKeys.length > 0 || unusedKeys.length > 0) {
    if (reportOutput.trim()) {
      console.error(reportOutput)
    }

    if (missingKeys.length > 0) {
      console.error('\nMissing translation keys:')
      for (const item of missingKeys) {
        console.error(`- ${item.path} (${item.language}) in ${item.file}`)
      }
    }

    if (unusedKeys.length > 0) {
      console.error('\nUnused translation keys:')
      for (const item of unusedKeys) {
        console.error(`- ${item.path} (${item.language}) in ${item.file}`)
      }
    }

    process.exit(1)
  }

  console.log(
    `\nI18n check passed (${missingKeys.length} missing, ${unusedKeys.length} unused).`
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
