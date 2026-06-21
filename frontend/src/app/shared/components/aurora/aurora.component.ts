import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { NgClass } from '@angular/common';

/**
 * Ambient decorative background: blurred glow blobs, drifting diamonds and
 * concentric arc SVGs. Direct conversion of Aurora.tsx — purely presentational,
 * pointer-events disabled throughout, used as a backdrop layer behind page content
 * (e.g. inside the authenticated app shell via SiteChrome).
 */
@Component({
  selector: 'app-aurora',
  standalone: true,
  imports: [NgClass],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './aurora.component.html',
  styleUrl: './aurora.component.scss',
})
export class AuroraComponent {
  @Input() className = '';

  /** Mirrors `[...Array(10)].map((_, i) => 40 + i * 18)` from the bottom-left arc cluster. */
  readonly bottomLeftRings = Array.from({ length: 10 }, (_, i) => 40 + i * 18);

  /** Mirrors `[...Array(8)].map((_, i) => 30 + i * 18)` from the top-right arc cluster. */
  readonly topRightRings = Array.from({ length: 8 }, (_, i) => 30 + i * 18);
}
