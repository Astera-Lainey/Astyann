import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LogoComponent } from '../logo/logo.component';
import { ThemeToggleComponent } from '../theme-toggle/theme-toggle.component';

/**
 * Sticky glass-morphism site header: logo, primary nav, theme toggle,
 * "Sign in" link and gradient "Start building" CTA.
 * Direct conversion of `SiteNav` from SiteChrome.tsx.
 */
@Component({
  selector: 'app-site-nav',
  standalone: true,
  imports: [RouterLink, LogoComponent, ThemeToggleComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './site-nav.component.html',
  styleUrl: './site-chrome.component.scss',
})
export class SiteNavComponent {}
