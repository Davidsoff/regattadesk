import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

// Accessibility test configuration for axe-core tests
export default defineConfig({
  plugins: [vue()],
  test: {
    globals: true,
    environment: 'happy-dom',
    include: ['tests/accessibility/**/*.a11y.test.js'],
    // Ensure tests are deterministic
    sequence: {
      shuffle: false
    },
    // Set reasonable timeout for accessibility scans
    testTimeout: 10000,
    // Output configuration
    reporters: ['default', 'json'],
    outputFile: {
      json: 'a11y-reports/test-results.json'
    }
  }
})
