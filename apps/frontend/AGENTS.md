# Agent Instructions: Frontend Application

## Context
This directory contains the Vue.js frontend application for RegattaDesk.

## Technology Stack
- **Framework**: Vue 3.5+
- **Build Tool**: Vite 7+
- **Language**: JavaScript (with future TypeScript support)
- **Package Manager**: npm

## Project Structure
```
apps/frontend/
├── src/
│   ├── assets/           # Static assets (images, styles)
│   ├── components/       # Vue components
│   ├── App.vue          # Root component
│   └── main.js          # Application entry point
├── public/              # Public static files
├── index.html           # HTML template
├── package.json         # Dependencies and scripts
└── vite.config.js       # Vite configuration
```

## Development Guidelines

### Running the Frontend
```bash
npm run dev
```
This starts the dev server with HMR on `http://localhost:5173`.

### Building
```bash
npm run build
```
Output will be in the `dist/` directory.

### Preview Production Build
```bash
npm run preview
```

### Adding Dependencies
```bash
npm install <package-name>       # Production dependency
npm install -D <package-name>    # Development dependency
```

### Code Conventions
- Use Vue 3 Composition API for new components
- Component files use PascalCase: `ComponentName.vue`
- Keep components small and focused
- Use props for parent-child communication
- Use events for child-parent communication

### Component Structure
```vue
<script setup>
// Component logic using Composition API
import { ref, computed } from 'vue'

const count = ref(0)
</script>

<template>
  <!-- Component template -->
</template>

<style scoped>
/* Component styles */
</style>
```

### Configuration
- Vite config: `vite.config.js`
- Proxy backend API requests through Vite during development
- Environment variables use `VITE_` prefix

## Important Notes for Agents

1. **Node Version**: Must be Node.js 22+ (specified in package.json engines)
2. **Vue Version**: 3.5+ is the baseline
3. **Build Tool**: Vite provides fast HMR and optimized builds
4. **Package Manager**: Use npm (not yarn or pnpm for consistency)
5. **Code Style**: Will be enforced by ESLint/Prettier in future iterations

## Common Commands
- `npm run dev` - Start dev server with HMR
- `npm run build` - Build for production
- `npm run preview` - Preview production build
- `npm install` - Install dependencies

## Future Enhancements
- TypeScript migration
- Vue Router for navigation
- Pinia for state management
- ESLint + Prettier for code quality
- Vitest for unit testing
- Cypress/Playwright for E2E testing
- Accessibility testing (axe-core)
- i18n support (English/Dutch)
