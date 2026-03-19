# FEGAP-008-D: Post-Draw Immutability Guards and Role-Based Visibility

## Overview

This implementation provides reusable composables and example components demonstrating frontend guardrails for post-draw immutability constraints and role-based visibility controls.

## Core Composables

### 1. `useUserRole()` - Role-Based Access Control

Located in: `src/composables/useUserRole.js`

Provides access to the current user's role and computed role checks.

**Usage:**
```javascript
import { useUserRole } from '@/composables/useUserRole'

const { role, isSuperAdmin, isStaff, loadRole } = useUserRole()

// Load role from auth context
await loadRole()

// Check role
if (isSuperAdmin.value) {
  // Show super_admin-only features
}

if (isStaff.value) {
  // Show staff features (includes super_admin)
}
```

**API:**
- `role` (Ref<string|null>): Current user role ('staff', 'super_admin', or null)
- `isSuperAdmin` (ComputedRef<boolean>): True if user is super_admin
- `isStaff` (ComputedRef<boolean>): True if user is staff or super_admin
- `loadRole()` (Function): Async function to load role from `globalThis.__REGATTADESK_AUTH__`

**Test Coverage:** 12 tests covering initialization, role loading, and computed properties

---

### 2. `useDrawImmutability()` - Post-Draw Immutability Checks

Located in: `src/composables/useDrawImmutability.js`

Provides helper functions to check draw publication state and determine editability.

**Usage:**
```javascript
import { 
  isDrawPublished, 
  canEditAfterDraw, 
  getImmutabilityMessage 
} from '@/composables/useDrawImmutability'

// Check if draw is published
if (isDrawPublished(regatta)) {
  console.log('Draw is published, data is immutable')
}

// Check if editing is allowed
const canEdit = canEditAfterDraw(regatta)

// Get user-friendly message
const message = getImmutabilityMessage(regatta, 'blocks')
// Returns: "Cannot edit blocks after draw publication. Use Unpublish in Draw Workflow to restore editability."
```

**API:**
- `isDrawPublished(regatta)`: Returns true if `regatta.draw_revision > 0`
- `canEditAfterDraw(regatta)`: Returns true if editing is allowed
- `getImmutabilityMessage(regatta, resourceType)`: Returns warning message or null
- `useDrawImmutability(regattaRef)`: Reactive composable version

**Test Coverage:** 15 tests covering various scenarios and edge cases

---

### 3. `useBibPoolValidation()` - Validation Error Handling

Located in: `src/composables/useBibPoolValidation.js`

Provides utilities to parse and display bib pool validation errors.

**Usage:**
```javascript
import { 
  parseBibPoolValidationError, 
  formatOverlappingBibs,
  isBibPoolValidationError,
  createValidationErrorMessage
} from '@/composables/useBibPoolValidation'

try {
  await saveBibPool(data)
} catch (error) {
  if (isBibPoolValidationError(error)) {
    const validationError = parseBibPoolValidationError(error)
    const bibsText = formatOverlappingBibs(validationError.overlappingBibs)
    
    console.log(`Bibs ${bibsText} overlap with ${validationError.conflictingPoolName}`)
    // Output: "Bibs 50, 51, 52, 100 overlap with Block A Pool"
  }
}
```

**API:**
- `isBibPoolValidationError(error)`: Checks if error code is 'BIB_POOL_VALIDATION_ERROR'
- `parseBibPoolValidationError(error)`: Extracts validation details
- `formatOverlappingBibs(bibs)`: Formats bib numbers as comma-separated string
- `createValidationErrorMessage(validationError)`: Creates user-friendly message

**Test Coverage:** 18 tests covering error parsing, formatting, and edge cases

---

## Example Components

### 1. BlocksManagementExample.vue

**Location:** `src/components/examples/BlocksManagementExample.vue`

Demonstrates immutability guards for block management.

**Features:**
- ✅ Warning banner when draw is published
- ✅ Lock indicators on immutable blocks
- ✅ Disabled edit/delete buttons with tooltips
- ✅ Helpful messages explaining why actions are disabled
- ✅ WCAG 2.2 AA compliant (alert roles, focus management)

**Usage Pattern:**
```vue
<script setup>
import { computed } from 'vue'
import { 
  isDrawPublished as checkDrawPublished, 
  canEditAfterDraw, 
  getImmutabilityMessage 
} from '@/composables/useDrawImmutability'

const props = defineProps({
  regatta: { type: Object, required: true }
})

const isDrawPublished = computed(() => checkDrawPublished(props.regatta))
const canEdit = computed(() => canEditAfterDraw(props.regatta))
const immutabilityMessage = computed(() => 
  getImmutabilityMessage(props.regatta, 'blocks')
)
</script>

<template>
  <div v-if="isDrawPublished" class="warning-banner" role="alert">
    {{ immutabilityMessage }}
  </div>
  
  <button 
    :disabled="!canEdit"
    :title="canEdit ? 'Edit block' : immutabilityMessage"
  >
    Edit
  </button>
</template>
```

**Test Coverage:** 16 tests covering all immutability guard scenarios

---

### 2. RulesetDetailExample.vue

**Location:** `src/components/examples/RulesetDetailExample.vue`

Demonstrates role-based visibility and immutability guards for rulesets.

**Features:**
- ✅ Promote button shown only to super_admin users
- ✅ 403 error handling with role-specific messages
- ✅ Combined role and immutability checks
- ✅ Clear visual badges for global vs. regatta rulesets
- ✅ Accessibility compliant error messages

**Usage Pattern:**
```vue
<script setup>
import { ref, computed, onMounted } from 'vue'
import { useUserRole } from '@/composables/useUserRole'
import { isDrawPublished, canEditAfterDraw } from '@/composables/useDrawImmutability'

const { isSuperAdmin, loadRole } = useUserRole()

onMounted(async () => {
  await loadRole()
})

const showPromoteButton = computed(() => 
  isSuperAdmin.value && !props.ruleset.is_global
)

const canEdit = computed(() => canEditAfterDraw(props.regatta))
</script>

<template>
  <button 
    v-if="showPromoteButton"
    :disabled="!canPromote"
    :title="isSuperAdmin ? 'Promote to global' : 'Requires super_admin role'"
  >
    Promote to Global
  </button>
  
  <button :disabled="!canEdit">Edit</button>
</template>
```

**Test Coverage:** 16 tests covering role-based visibility and immutability

---

### 3. BibPoolFormExample.vue

**Location:** `src/components/examples/BibPoolFormExample.vue`

Demonstrates validation error display for bib pool forms.

**Features:**
- ✅ Formatted display of overlapping bib numbers
- ✅ Conflicting pool information display
- ✅ Highlighted invalid form fields
- ✅ Clear call-to-action for fixing errors
- ✅ Combined with immutability guards

**Usage Pattern:**
```vue
<script setup>
import { ref, computed } from 'vue'
import { 
  parseBibPoolValidationError, 
  formatOverlappingBibs,
  isBibPoolValidationError 
} from '@/composables/useBibPoolValidation'

const validationError = ref(null)

const hasOverlapError = computed(() => 
  validationError.value?.overlappingBibs?.length > 0
)

const formattedBibs = computed(() => 
  formatOverlappingBibs(validationError.value?.overlappingBibs)
)

async function handleSubmit() {
  try {
    await saveBibPool(formData.value)
  } catch (error) {
    if (isBibPoolValidationError(error)) {
      validationError.value = parseBibPoolValidationError(error)
    }
  }
}
</script>

<template>
  <div v-if="validationError" class="validation-error" role="alert">
    <p><strong>Overlapping bib numbers:</strong> {{ formattedBibs }}</p>
    <p><strong>Conflicts with:</strong> {{ validationError.conflictingPoolName }}</p>
    <p>Change the bib range or delete the conflicting pool.</p>
  </div>
  
  <input 
    :class="{ 'has-error': hasOverlapError }"
    v-model="formData.startBib"
  />
</template>
```

**Test Coverage:** 22 tests covering validation display and form behavior

---

## Implementation Patterns

### Pattern 1: Basic Immutability Guard

```vue
<script setup>
import { computed } from 'vue'
import { canEditAfterDraw, getImmutabilityMessage } from '@/composables/useDrawImmutability'

const props = defineProps({
  regatta: { type: Object, required: true }
})

const canEdit = computed(() => canEditAfterDraw(props.regatta))
const tooltip = computed(() => {
  return canEdit.value 
    ? 'Edit item' 
    : getImmutabilityMessage(props.regatta, 'item')
})
</script>

<template>
  <button :disabled="!canEdit" :title="tooltip">Edit</button>
</template>
```

### Pattern 2: Role-Based Visibility

```vue
<script setup>
import { useUserRole } from '@/composables/useUserRole'
import { onMounted } from 'vue'

const { isSuperAdmin, loadRole } = useUserRole()

onMounted(async () => {
  await loadRole()
})
</script>

<template>
  <button v-if="isSuperAdmin">Admin Action</button>
</template>
```

### Pattern 3: Combined Guards

```vue
<script setup>
import { computed, onMounted } from 'vue'
import { useUserRole } from '@/composables/useUserRole'
import { canEditAfterDraw } from '@/composables/useDrawImmutability'

const { isSuperAdmin, loadRole } = useUserRole()

const canPerformAction = computed(() => 
  isSuperAdmin.value && canEditAfterDraw(props.regatta)
)

onMounted(async () => {
  await loadRole()
})
</script>

<template>
  <button :disabled="!canPerformAction">
    Protected Action
  </button>
</template>
```

### Pattern 4: Validation Error Display

```vue
<script setup>
import { ref } from 'vue'
import { 
  parseBibPoolValidationError, 
  formatOverlappingBibs 
} from '@/composables/useBibPoolValidation'

const validationError = ref(null)

async function submit() {
  try {
    validationError.value = null
    await saveData()
  } catch (error) {
    validationError.value = parseBibPoolValidationError(error)
  }
}
</script>

<template>
  <div v-if="validationError" role="alert">
    {{ validationError.message }}
    <p>Overlapping: {{ formatOverlappingBibs(validationError.overlappingBibs) }}</p>
  </div>
</template>
```

---

## Testing

### Running Tests

```bash
# Run all tests
npm test

# Run specific composable tests
npm test -- useUserRole.test.js --run
npm test -- useDrawImmutability.test.js --run
npm test -- useBibPoolValidation.test.js --run

# Run example component tests
npm test -- BlocksManagementExample.test.js --run
npm test -- RulesetDetailExample.test.js --run
npm test -- BibPoolFormExample.test.js --run
```

### Test Summary

| Component/Composable | Tests | Status |
|---------------------|-------|--------|
| useUserRole | 12 | ✅ Passing |
| useDrawImmutability | 15 | ✅ Passing |
| useBibPoolValidation | 18 | ✅ Passing |
| BlocksManagementExample | 16 | ✅ Passing |
| RulesetDetailExample | 16 | ✅ Passing |
| BibPoolFormExample | 22 | ✅ Passing |
| **Total** | **99** | ✅ **All Passing** |

---

## Accessibility Compliance

All components meet WCAG 2.2 AA requirements:

- ✅ Alert roles on error messages and warnings
- ✅ Descriptive tooltips on disabled buttons
- ✅ Proper label associations for form inputs
- ✅ Keyboard navigation support
- ✅ Color contrast ratios meet AA standards
- ✅ Focus indicators visible and clear

---

## Integration Guide

### For Future Component Implementation

When implementing actual components (not examples), follow these steps:

1. **Import the composables:**
   ```javascript
   import { useUserRole } from '@/composables/useUserRole'
   import { isDrawPublished, canEditAfterDraw } from '@/composables/useDrawImmutability'
   import { parseBibPoolValidationError } from '@/composables/useBibPoolValidation'
   ```

2. **Set up reactive state:**
   ```javascript
   const { isSuperAdmin, loadRole } = useUserRole()
   const canEdit = computed(() => canEditAfterDraw(regatta.value))
   
   onMounted(async () => {
     await loadRole()
   })
   ```

3. **Use in template:**
   ```vue
   <button v-if="isSuperAdmin" :disabled="!canEdit">
     Protected Action
   </button>
   ```

4. **Add proper error handling:**
   ```javascript
   try {
     await apiCall()
   } catch (error) {
     if (error.status === 403) {
       errorMessage.value = 'This action requires super_admin role'
     }
   }
   ```

5. **Test thoroughly:**
   - Unit tests for logic
   - Component tests for UI behavior
   - Manual testing with different roles
   - Manual testing with published/unpublished draws

---

## API Error Codes

The composables handle these error codes:

| Error Code | Description | Handler |
|-----------|-------------|---------|
| `BIB_POOL_VALIDATION_ERROR` | Bib range overlaps | useBibPoolValidation |
| `403` / `FORBIDDEN` | Insufficient permissions | useUserRole (implicit) |

---

## Future Enhancements

Potential improvements for v0.2+:

1. **Enhanced Role System:**
   - Support for custom roles beyond staff/super_admin
   - Granular permissions per feature
   - Role hierarchy and inheritance

2. **Advanced Validation:**
   - Client-side pre-validation before API call
   - Debounced validation for real-time feedback
   - Multi-field validation coordination

3. **Improved UX:**
   - Inline edit mode with undo
   - Draft mode for batch changes
   - Optimistic UI updates with rollback

4. **Monitoring:**
   - Track immutability guard triggers
   - Log role-based access attempts
   - Validation error analytics

---

## Security Considerations

⚠️ **Important:** These components provide **UI guardrails only**.

- Backend must enforce all authorization rules
- Backend must validate all immutability constraints
- Frontend guards improve UX but are not security boundaries
- Never trust client-side role checks for authorization

The `__REGATTADESK_AUTH__` global is injected by the authentication proxy (Authelia via Traefik) and reflects server-side session state.

---

## Support and Questions

For questions or issues:

1. Check the example components for reference implementations
2. Review the test files for usage patterns
3. Consult the main product design documents in `docs/specs/pdd-v0.1/design/`
4. Refer to BC04 implementation spec: `docs/specs/pdd-v0.1/implementation/bc04-rules-scheduling-and-draw.md`

---

## Change Log

### v0.1.0 (2026-02-27)
- ✅ Initial implementation of useUserRole composable
- ✅ Initial implementation of useDrawImmutability utilities
- ✅ Initial implementation of useBibPoolValidation utilities
- ✅ Example components with comprehensive test coverage
- ✅ Documentation and usage patterns
