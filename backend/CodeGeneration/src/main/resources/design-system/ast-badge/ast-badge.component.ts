import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'ast-badge',
  standalone: true,
  templateUrl: './ast-badge.component.html',
  styleUrls: ['./ast-badge.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AstBadgeComponent {
  @Input() variant: 'success' | 'warning' | 'danger' | 'info' | 'neutral' = 'neutral';
}
