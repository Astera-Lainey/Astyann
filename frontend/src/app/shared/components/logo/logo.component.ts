import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-logo',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a routerLink="/" class="logo">
      <img src="/logo.png" alt="Astyann" class="logo__mark" />
      Astyann
    </a>
  `,
  styles: [`
    .logo {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      font-family: var(--font-display);
      font-size: 1.25rem;
      font-weight: 400;
      letter-spacing: -0.018em;
      color: var(--color-foreground);
      text-decoration: none;
    }
    .logo__mark {
      height: 24px;
      width: 24px;
      object-fit: contain;
    }
  `],
})
export class LogoComponent {}
