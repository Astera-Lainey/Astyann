import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

/**
 * Decorative 4-pointed sparkle icon.
 *
 * React source defined this identically inline in index.tsx, login.tsx and
 * signup.tsx — extracted here into a single reusable standalone component.
 */
@Component({
  selector: 'app-sparkle-icon',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg
      [attr.class]="className"
      [attr.width]="size"
      [attr.height]="size"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      [attr.stroke-width]="strokeWidth"
      stroke-linecap="round"
    >
      <path d="M12 1.5 L12 22.5 M1.5 12 L22.5 12 M4.5 4.5 L19.5 19.5 M19.5 4.5 L4.5 19.5" />
    </svg>
  `,
})
export class SparkleIconComponent {
  @Input() className = '';
  @Input() size = 22;
  /** login/signup pages render this at 1.2, the landing page at 1 — kept configurable. */
  @Input() strokeWidth: number | string = 1.2;
}
