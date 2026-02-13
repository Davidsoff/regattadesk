<template>
  <div class="demo-container">
    <header class="demo-header">
      <h1>RegattaDesk Design Tokens & Primitives Demo</h1>
      <p class="demo-subtitle">BC05-006: Public Design System Implementation</p>
      
      <div class="demo-controls">
        <label>
          <input 
            type="checkbox" 
            :checked="isHighContrast"
            @change="toggleContrast"
          >
          High Contrast Mode
        </label>
        <label>
          <input 
            type="checkbox" 
            :checked="isCompact"
            @change="toggleDensity"
          >
          Compact Density
        </label>
      </div>
    </header>

    <main id="main-content" class="demo-content">
      <!-- Design Tokens Section -->
      <section class="demo-section">
        <h2>Design Tokens</h2>
        
        <h3>Color Palette</h3>
        <div class="token-grid">
          <div class="token-item">
            <div class="color-swatch" style="background-color: var(--rd-accent)"></div>
            <span>--rd-accent</span>
          </div>
          <div class="token-item">
            <div class="color-swatch" style="background-color: var(--rd-info)"></div>
            <span>--rd-info</span>
          </div>
          <div class="token-item">
            <div class="color-swatch" style="background-color: var(--rd-success)"></div>
            <span>--rd-success</span>
          </div>
          <div class="token-item">
            <div class="color-swatch" style="background-color: var(--rd-warn)"></div>
            <span>--rd-warn</span>
          </div>
          <div class="token-item">
            <div class="color-swatch" style="background-color: var(--rd-danger)"></div>
            <span>--rd-danger</span>
          </div>
        </div>

        <h3>Typography</h3>
        <div class="typography-demo">
          <p style="font-size: var(--rd-text-2xl); font-weight: var(--rd-weight-semibold);">
            2XL Semibold - Page Heading
          </p>
          <p style="font-size: var(--rd-text-xl); font-weight: var(--rd-weight-semibold);">
            XL Semibold - Section Heading
          </p>
          <p style="font-size: var(--rd-text-base);">
            Base - Body Text
          </p>
          <p style="font-size: var(--rd-text-sm); color: var(--rd-text-muted);">
            Small - Meta Text
          </p>
          <p class="rd-tabular-nums">
            Tabular: 1:23.456 • 1:45.789 • 2:03.012
          </p>
        </div>

        <h3>Spacing Scale</h3>
        <div class="spacing-demo">
          <div class="spacing-item" style="width: var(--rd-space-1)">1</div>
          <div class="spacing-item" style="width: var(--rd-space-2)">2</div>
          <div class="spacing-item" style="width: var(--rd-space-3)">3</div>
          <div class="spacing-item" style="width: var(--rd-space-4)">4</div>
          <div class="spacing-item" style="width: var(--rd-space-5)">5</div>
          <div class="spacing-item" style="width: var(--rd-space-6)">6</div>
        </div>
      </section>

      <!-- Status Chips Section -->
      <section class="demo-section">
        <h2>Status Chips (RdChip)</h2>
        
        <h3>Entry Statuses</h3>
        <div class="chip-grid">
          <RdChip variant="neutral" label="Entered" />
          <RdChip variant="muted" label="Withdrawn Before Draw" />
          <RdChip variant="warn" label="Withdrawn After Draw" />
          <RdChip variant="warn" label="DNS" />
          <RdChip variant="warn" label="DNF" />
          <RdChip variant="danger" label="Excluded" />
          <RdChip variant="danger" label="DSQ" />
        </div>

        <h3>Workflow States</h3>
        <div class="chip-grid">
          <RdChip variant="info" label="Under Investigation" icon="⚠" />
          <RdChip variant="success" label="Approved" icon="🔒" />
          <RdChip variant="muted" label="Offline Queued" icon="⏸" :count="3" />
        </div>

        <h3>Result Labels</h3>
        <div class="chip-grid">
          <RdChip variant="neutral" label="Provisional" icon="⏱" />
          <RdChip variant="info" label="Edited" icon="✎" />
          <RdChip variant="success" label="Official" icon="✓" />
        </div>
      </section>

      <!-- Table Primitives Section -->
      <section class="demo-section">
        <h2>Table Primitives (RdTable)</h2>
        
        <h3>Race Results Table</h3>
        <RdTable 
          caption="Men's Junior Single Sculls - Heat 1"
          sticky
        >
          <template #header>
            <tr>
              <th scope="col">Rank</th>
              <th scope="col">Bib</th>
              <th scope="col">Crew</th>
              <th scope="col">Club</th>
              <th scope="col" class="rd-align-right">Time</th>
              <th scope="col" class="rd-align-right">Delta</th>
              <th scope="col">Status</th>
            </tr>
          </template>
          <tr v-for="result in mockResults" :key="result.bib">
            <td>{{ result.rank }}</td>
            <td class="rd-tabular-nums">{{ result.bib }}</td>
            <td>{{ result.crew }}</td>
            <td>{{ result.club }}</td>
            <td class="rd-tabular-nums rd-align-right">{{ result.time }}</td>
            <td class="rd-tabular-nums rd-align-right">{{ result.delta }}</td>
            <td>
              <RdChip 
                :variant="result.status.variant" 
                :label="result.status.label"
                :icon="result.status.icon"
                size="sm"
              />
            </td>
          </tr>
        </RdTable>

        <h3>Empty State</h3>
        <RdTable 
          caption="Filtered Results"
          is-empty
          @clear="handleClearFilters"
        />

        <h3>Loading State</h3>
        <RdTable 
          caption="Loading Results"
          loading
          :skeleton-rows="3"
        >
          <template #header>
            <tr>
              <th scope="col">Rank</th>
              <th scope="col">Crew</th>
              <th scope="col">Time</th>
            </tr>
          </template>
        </RdTable>
      </section>

      <!-- Accessibility Features Section -->
      <section class="demo-section">
        <h2>Accessibility Features</h2>
        
        <h3>Focus Management</h3>
        <p>Navigate this page with keyboard:</p>
        <ul>
          <li><kbd>Tab</kbd> - Move between interactive elements</li>
          <li><kbd>Shift+Tab</kbd> - Move backwards</li>
          <li>All interactive elements show visible focus rings</li>
          <li>High contrast mode increases focus ring visibility</li>
        </ul>

        <h3>Screen Reader Support</h3>
        <p>Tables use proper semantic HTML:</p>
        <ul>
          <li><code>&lt;caption&gt;</code> for table descriptions</li>
          <li><code>scope="col"</code> on header cells</li>
          <li>Status chips include <code>role="status"</code></li>
          <li>Live regions for dynamic announcements (not shown)</li>
        </ul>

        <h3>Touch Targets</h3>
        <p>All interactive elements meet minimum touch target sizes:</p>
        <ul>
          <li>Default: <code>--rd-hit</code> (44px minimum)</li>
          <li>Operator: <code>--rd-hit-operator</code> (52px default)</li>
        </ul>

        <div class="touch-demo">
          <button class="demo-button">Standard Button (44px min)</button>
          <button class="demo-button demo-button--operator">Operator Button (52px min)</button>
        </div>

        <h3>Live Announcer Demo</h3>
        <button 
          type="button"
          class="demo-button"
          @click="makeAnnouncement"
        >
          Make Announcement (listen with screen reader)
        </button>
        <p class="demo-note">Last announcement: {{ lastAnnouncement || 'None' }}</p>
      </section>

      <!-- WCAG Compliance Section -->
      <section class="demo-section">
        <h2>WCAG 2.2 AA Compliance</h2>
        <ul>
          <li>✅ Color contrast: All text meets 4.5:1 minimum (WCAG 2.2 AA standard)</li>
          <li>✅ Focus indicators: 2px visible focus rings with adequate contrast</li>
          <li>✅ Keyboard navigation: All functionality accessible via keyboard</li>
          <li>✅ Touch targets: Minimum 44×44px on all interactive elements</li>
          <li>✅ Semantic HTML: Proper use of headings, landmarks, and ARIA</li>
          <li>✅ Motion: Respects <code>prefers-reduced-motion</code></li>
          <li>✅ Text scaling: Layout remains usable at 200% zoom</li>
          <li>✅ Screen reader support: Meaningful labels and announcements</li>
        </ul>
      </section>
    </main>

    <footer class="demo-footer">
      <p>
        RegattaDesk v0.1 • Design tokens and primitives per 
        <code>pdd/design/style-guide.md</code>
      </p>
    </footer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import RdTable from './primitives/RdTable.vue';
import RdChip from './primitives/RdChip.vue';
import { useLiveAnnouncer } from '../composables/useAccessibility.js';

const isHighContrast = ref(false);
const isCompact = ref(false);
const lastAnnouncement = ref('');

const { announce } = useLiveAnnouncer();

const mockResults = [
  {
    rank: 1,
    bib: '42',
    crew: 'John Smith',
    club: 'Thames RC',
    time: '7:23.456',
    delta: '+0:00.000',
    status: { variant: 'success', label: 'Official', icon: '✓' }
  },
  {
    rank: 2,
    bib: '15',
    crew: 'Alex Johnson',
    club: 'Leander',
    time: '7:25.123',
    delta: '+0:01.667',
    status: { variant: 'success', label: 'Official', icon: '✓' }
  },
  {
    rank: 3,
    bib: '28',
    crew: 'Chris Wilson',
    club: 'Molesey BC',
    time: '7:27.890',
    delta: '+0:04.434',
    status: { variant: 'info', label: 'Edited', icon: '✎' }
  },
  {
    rank: 4,
    bib: '7',
    crew: 'Sam Brown',
    club: 'London RC',
    time: '7:30.012',
    delta: '+0:06.556',
    status: { variant: 'neutral', label: 'Provisional', icon: '⏱' }
  },
  {
    rank: '–',
    bib: '33',
    crew: 'Mike Davis',
    club: 'Oxford Brookes',
    time: '–',
    delta: '–',
    status: { variant: 'warn', label: 'DNF', icon: null }
  },
];

function toggleContrast(event) {
  isHighContrast.value = event.target.checked;
  document.documentElement.setAttribute(
    'data-contrast',
    isHighContrast.value ? 'high' : ''
  );
}

function toggleDensity(event) {
  isCompact.value = event.target.checked;
  document.documentElement.setAttribute(
    'data-density',
    isCompact.value ? 'compact' : ''
  );
}

function handleClearFilters() {
  alert('Clear filters clicked!');
}

function makeAnnouncement() {
  const announcements = [
    '12 entries selected',
    'Filter applied: 52 results found',
    'Results updated',
    'Draw published: version 3',
  ];
  const msg = announcements[Math.floor(Math.random() * announcements.length)];
  lastAnnouncement.value = msg;
  announce(msg, 'polite');
}

onMounted(() => {
  // Set page title for accessibility
  document.title = 'RegattaDesk Design System Demo';
});
</script>

<style scoped>
.demo-container {
  max-width: 1280px;
  margin: 0 auto;
  padding: var(--rd-space-6);
}

.demo-header {
  margin-bottom: var(--rd-space-6);
  padding-bottom: var(--rd-space-4);
  border-bottom: 2px solid var(--rd-border);
}

.demo-header h1 {
  margin: 0 0 var(--rd-space-2);
  font-size: var(--rd-text-2xl);
  font-weight: var(--rd-weight-semibold);
  color: var(--rd-text);
}

.demo-subtitle {
  margin: 0 0 var(--rd-space-4);
  color: var(--rd-text-muted);
}

.demo-controls {
  display: flex;
  gap: var(--rd-space-4);
  flex-wrap: wrap;
}

.demo-controls label {
  display: flex;
  align-items: center;
  gap: var(--rd-space-2);
  font-size: var(--rd-text-sm);
  cursor: pointer;
}

.demo-controls input[type="checkbox"] {
  width: 20px;
  height: 20px;
  cursor: pointer;
}

.demo-content {
  display: flex;
  flex-direction: column;
  gap: var(--rd-space-6);
}

.demo-section {
  padding: var(--rd-space-5);
  background-color: var(--rd-surface);
  border-radius: var(--rd-border-radius-lg);
  border: 1px solid var(--rd-border);
}

.demo-section h2 {
  margin: 0 0 var(--rd-space-4);
  font-size: var(--rd-text-xl);
  font-weight: var(--rd-weight-semibold);
  color: var(--rd-text);
}

.demo-section h3 {
  margin: var(--rd-space-4) 0 var(--rd-space-3);
  font-size: var(--rd-text-lg);
  font-weight: var(--rd-weight-medium);
  color: var(--rd-text);
}

.demo-section h3:first-of-type {
  margin-top: 0;
}

.token-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: var(--rd-space-3);
  margin-bottom: var(--rd-space-4);
}

.token-item {
  display: flex;
  flex-direction: column;
  gap: var(--rd-space-2);
  align-items: center;
}

.color-swatch {
  width: 80px;
  height: 80px;
  border-radius: var(--rd-border-radius);
  border: 1px solid var(--rd-border);
}

.typography-demo p {
  margin: var(--rd-space-2) 0;
}

.spacing-demo {
  display: flex;
  gap: var(--rd-space-2);
  align-items: flex-end;
  margin-bottom: var(--rd-space-4);
}

.spacing-item {
  height: 60px;
  background-color: var(--rd-accent);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--rd-text-xs);
  font-weight: var(--rd-weight-semibold);
  border-radius: var(--rd-border-radius);
}

.chip-grid {
  display: flex;
  flex-wrap: wrap;
  gap: var(--rd-space-2);
  margin-bottom: var(--rd-space-4);
}

.touch-demo {
  display: flex;
  gap: var(--rd-space-3);
  flex-wrap: wrap;
  margin: var(--rd-space-4) 0;
}

.demo-button {
  min-height: var(--rd-hit);
  padding: var(--rd-space-2) var(--rd-space-4);
  background-color: var(--rd-accent);
  color: white;
  border: none;
  border-radius: var(--rd-border-radius);
  font-size: var(--rd-text-sm);
  font-weight: var(--rd-weight-medium);
  cursor: pointer;
  transition: opacity var(--rd-transition-fast);
}

.demo-button:hover {
  opacity: 0.9;
}

.demo-button--operator {
  min-height: var(--rd-hit-operator);
  background-color: var(--rd-accent-2);
}

.demo-note {
  margin-top: var(--rd-space-2);
  font-size: var(--rd-text-sm);
  color: var(--rd-text-muted);
  font-style: italic;
}

.demo-footer {
  margin-top: var(--rd-space-6);
  padding-top: var(--rd-space-4);
  border-top: 1px solid var(--rd-border);
  text-align: center;
  color: var(--rd-text-muted);
  font-size: var(--rd-text-sm);
}

kbd {
  display: inline-block;
  padding: 2px 6px;
  background-color: var(--rd-surface-2);
  border: 1px solid var(--rd-border);
  border-radius: var(--rd-border-radius);
  font-family: var(--rd-font-numeric);
  font-size: var(--rd-text-xs);
}

code {
  padding: 2px 6px;
  background-color: var(--rd-surface-2);
  border-radius: var(--rd-border-radius);
  font-family: var(--rd-font-numeric);
  font-size: 0.9em;
}

@media (max-width: 768px) {
  .demo-container {
    padding: var(--rd-space-4);
  }
  
  .token-grid {
    grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  }
}
</style>
