import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

/**
 * Decorative outline-circle clusters used on the auth pages' left panel.
 *
 * React source: `Circles({ position })` in login.tsx (top-left "tl" / bottom-left "bl"),
 * and the same two SVGs duplicated inline (unnamed) in signup.tsx. Both are
 * unified here behind a single `position` input.
 */
@Component({
  selector: 'app-decorative-circles',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (position === 'tl') {
      <svg
        class="circles circles--tl"
        viewBox="0 0 200 200"
        fill="none"
        stroke="currentColor"
        stroke-width="0.6"
      >
        <circle cx="100" cy="60" r="80" />
        <circle cx="120" cy="50" r="70" />
      </svg>
    } @else {
      <svg
        class="circles circles--bl"
        viewBox="0 0 200 200"
        fill="none"
        stroke="currentColor"
        stroke-width="0.5"
      >
        <circle cx="100" cy="100" r="95" />
      </svg>
    }
  `,
  styleUrl: './decorative-circles.component.scss',
})
export class DecorativeCirclesComponent {
  @Input() position: 'tl' | 'bl' = 'tl';
}
