import { ChangeDetectionStrategy, Component, signal } from '@angular/core';

/**
 * NOTE: ThemeToggle.tsx was referenced by SiteChrome.tsx but not included in
 * the provided React source files. Reconstructed as a minimal light/dark
 * toggle driven by an Angular Signal, toggling the `.dark` class on <html>
 * to match the `dark:` variants used throughout styles.css.
 */
@Component({
  selector: 'app-theme-toggle',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      type="button"
      class="theme-toggle"
      [attr.aria-label]="isDark() ? 'Switch to light mode' : 'Switch to dark mode'"
      (click)="toggle()"
    >
      @if (isDark()) {
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="4" />
          <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41" />
        </svg>
      } @else {
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
        </svg>
      }
    </button>
  `,
  styles: [`
    .theme-toggle {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 2.25rem;
      height: 2.25rem;
      border-radius: var(--radius-md);
      color: var(--color-muted-foreground);
      background: transparent;
      border: none;
      cursor: pointer;
      transition: color 0.2s, background-color 0.2s;
    }
    .theme-toggle:hover {
      color: var(--color-foreground);
      background-color: var(--color-secondary);
    }
  `],
})
export class ThemeToggleComponent {
  private readonly darkMode = signal(this.readInitialPreference());

  readonly isDark = this.darkMode.asReadonly();

  toggle(): void {
    const next = !this.darkMode();
    this.darkMode.set(next);
    document.documentElement.classList.toggle('dark', next);
    localStorage.setItem('astyann_theme', next ? 'dark' : 'light');
  }

  private readInitialPreference(): boolean {
    const stored = localStorage.getItem('astyann_theme');
    if (stored) {
      return stored === 'dark';
    }
    return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false;
  }
}
