/* ── PCSF COLOURS (generated) ─────────────────────────────────────────── */
:root {
  --color-primary: ${project.primaryColour!"#3F51B5"};
  --color-primary-dark: ${project.primaryDark!"#2C3A85"};
  --color-primary-light: ${project.primaryLight!"#7D8EDB"};
  --color-primary-alpha: ${project.primaryAlpha!"rgba(63, 81, 181, 0.15)"};
  --color-secondary: ${project.secondaryColour!"#FF4081"};
  --color-neutral: ${project.neutralColour!"#F4F5F7"};
  --color-text: ${project.textColour!"#1F2933"};
  --font-family: ${project.fontFamily!"Inter"}, "Segoe UI", Roboto, sans-serif;
}

/* ── TYPOGRAPHY ───────────────────────────────────────────────────────── */
:root {
  --font-size-xs: 0.75rem;
  --font-size-sm: 0.875rem;
  --font-size-md: 1rem;
  --font-size-lg: 1.125rem;
  --font-size-xl: 1.5rem;
  --font-size-2xl: 2rem;
  --font-weight-regular: 400;
  --font-weight-medium: 500;
  --font-weight-bold: 700;
  --line-height-tight: 1.25;
  --line-height-base: 1.5;
}

/* ── SPACING ──────────────────────────────────────────────────────────── */
:root {
  --space-1: 0.25rem;
  --space-2: 0.5rem;
  --space-3: 0.75rem;
  --space-4: 1rem;
  --space-5: 1.25rem;
  --space-6: 1.5rem;
  --space-8: 2rem;
  --space-10: 2.5rem;
}

/* ── SHAPE ────────────────────────────────────────────────────────────── */
:root {
  --radius-sm: 4px;
  --radius-md: 8px;
  --radius-lg: 12px;
  --radius-full: 999px;
}

/* ── SHADOWS ──────────────────────────────────────────────────────────── */
:root {
  --shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.05);
  --shadow-md: 0 4px 6px rgba(0, 0, 0, 0.08);
  --shadow-lg: 0 10px 24px rgba(0, 0, 0, 0.12);
}

/* ── MOTION ───────────────────────────────────────────────────────────── */
:root {
  --transition-fast: 120ms ease-out;
  --transition-base: 200ms ease-out;
}

/* ── SEMANTIC COLOURS ─────────────────────────────────────────────────── */
:root {
  --color-success: #16a34a;
  --color-warning: #f59e0b;
  --color-danger: #dc2626;
  --color-info: #0284c7;
  --color-border: #e5e7eb;
  --color-surface: #ffffff;
  --color-background: #f9fafb;
  --color-muted: #6b7280;
}

/* ── BASE RESET ───────────────────────────────────────────────────────── */
*, *::before, *::after { box-sizing: border-box; }
html, body { height: 100%; margin: 0; padding: 0; }
body {
  font-family: var(--font-family);
  font-size: var(--font-size-md);
  line-height: var(--line-height-base);
  color: var(--color-text);
  background: var(--color-background);
}
a { color: var(--color-primary); text-decoration: none; }
a:hover { text-decoration: underline; }

/* ── UTILITY CLASSES ──────────────────────────────────────────────────── */
.page-container { padding: var(--space-6); max-width: 1200px; margin: 0 auto; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--space-6); }
.page-title { font-size: var(--font-size-2xl); font-weight: var(--font-weight-bold); margin: 0; }
.login-container { display: flex; align-items: center; justify-content: center; min-height: 100vh; padding: var(--space-6); }

/* ── COMPONENT STYLES ─────────────────────────────────────────────────── */
.ast-btn {
  display: inline-flex; align-items: center; justify-content: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-4);
  border-radius: var(--radius-md);
  border: 1px solid transparent;
  font-family: inherit;
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-medium);
  cursor: pointer;
  transition: background var(--transition-fast), box-shadow var(--transition-fast);
}
.ast-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.ast-btn--primary { background: var(--color-primary); color: #fff; }
.ast-btn--primary:hover:not(:disabled) { background: var(--color-primary-dark); }
.ast-btn--secondary { background: var(--color-secondary); color: #fff; }
.ast-btn--danger { background: var(--color-danger); color: #fff; }
.ast-btn--ghost { background: transparent; color: var(--color-primary); border-color: var(--color-border); }
.ast-btn--sm { padding: var(--space-1) var(--space-3); font-size: var(--font-size-sm); }
.ast-btn--lg { padding: var(--space-3) var(--space-5); font-size: var(--font-size-lg); }

.ast-input {
  display: flex; flex-direction: column; gap: var(--space-1);
  font-size: var(--font-size-sm);
}
.ast-input__label { color: var(--color-muted); font-weight: var(--font-weight-medium); }
.ast-input__control {
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  font-size: var(--font-size-md);
  background: var(--color-surface);
  color: var(--color-text);
}
.ast-input__control:focus { outline: none; border-color: var(--color-primary); box-shadow: 0 0 0 3px var(--color-primary-alpha); }
.ast-input--error .ast-input__control { border-color: var(--color-danger); }
.ast-input__error { color: var(--color-danger); font-size: var(--font-size-xs); }

.ast-select { composes: ast-input; }

.ast-card {
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  padding: var(--space-6);
  border: 1px solid var(--color-border);
}

.ast-badge {
  display: inline-block;
  padding: var(--space-1) var(--space-2);
  font-size: var(--font-size-xs);
  border-radius: var(--radius-full);
  color: #fff;
  background: var(--color-muted);
}
.ast-badge--success { background: var(--color-success); }
.ast-badge--warning { background: var(--color-warning); color: #1f2933; }
.ast-badge--danger { background: var(--color-danger); }
.ast-badge--info { background: var(--color-info); }

.ast-table { width: 100%; border-collapse: collapse; }
.ast-table th, .ast-table td { padding: var(--space-3); text-align: left; border-bottom: 1px solid var(--color-border); }
.ast-table th { color: var(--color-muted); font-weight: var(--font-weight-medium); font-size: var(--font-size-sm); }
.ast-table tr:hover { background: var(--color-neutral); }

.ast-sidebar {
  display: flex; flex-direction: column; gap: var(--space-4);
  width: 240px; min-height: 100vh; padding: var(--space-6);
  background: var(--color-surface); border-right: 1px solid var(--color-border);
}
.ast-sidebar__brand { font-size: var(--font-size-lg); font-weight: var(--font-weight-bold); color: var(--color-primary); }
.ast-sidebar__nav { display: flex; flex-direction: column; gap: var(--space-1); }
.ast-sidebar__item {
  display: flex; align-items: center; gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  color: var(--color-text);
}
.ast-sidebar__item:hover { background: var(--color-neutral); text-decoration: none; }
.ast-sidebar__item--active { background: var(--color-primary-alpha); color: var(--color-primary); font-weight: var(--font-weight-medium); }
.ast-sidebar__logout { margin-top: auto; }

.ast-empty-state {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: var(--space-10) var(--space-6); gap: var(--space-2);
  color: var(--color-muted); text-align: center;
}

.ast-spinner {
  display: inline-block; width: 32px; height: 32px;
  border: 3px solid var(--color-primary-alpha);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: ast-spin 800ms linear infinite;
}
@keyframes ast-spin { to { transform: rotate(360deg); } }

.ast-alert {
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  font-size: var(--font-size-sm);
  border: 1px solid transparent;
  margin-bottom: var(--space-4);
}
.ast-alert--success { background: #f0fdf4; border-color: #bbf7d0; color: #166534; }
.ast-alert--warning { background: #fffbeb; border-color: #fde68a; color: #92400e; }
.ast-alert--danger { background: #fef2f2; border-color: #fecaca; color: #b91c1c; }
.ast-alert--info { background: #eff6ff; border-color: #bfdbfe; color: #1e40af; }

.ast-form { display: flex; flex-direction: column; gap: var(--space-4); }
.ast-form__actions { display: flex; justify-content: flex-end; gap: var(--space-3); margin-top: var(--space-4); }
.data-table { composes: ast-table; }
