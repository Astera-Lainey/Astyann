import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'ast-empty-state',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="ast-empty-state">
      <h3 class="ast-empty-state__title">{{ title }}</h3>
      @if (subtitle) { <p class="ast-empty-state__subtitle">{{ subtitle }}</p> }
      <ng-content></ng-content>
    </div>
  `,
  styles: [':host { display: block; }'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AstEmptyStateComponent {
  @Input() title = 'Nothing here yet';
  @Input() subtitle: string | null = null;
}
