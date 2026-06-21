import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * NOTE: Logo.tsx was referenced by SiteChrome.tsx but not included in the
 * provided React source files. This is a minimal text-based placeholder —
 * swap the template for the real mark/wordmark when available.
 */
@Component({
  selector: 'app-logo',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a routerLink="/" class="logo">Astyann</a>
  `,
  styles: [`
    .logo {
      font-family: var(--font-display);
      font-size: 1.25rem;
      font-weight: 400;
      letter-spacing: -0.018em;
      color: var(--color-foreground);
      text-decoration: none;
    }
  `],
})
export class LogoComponent {}
