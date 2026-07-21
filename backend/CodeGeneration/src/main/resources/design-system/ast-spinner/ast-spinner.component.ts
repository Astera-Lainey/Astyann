import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'ast-spinner',
  standalone: true,
  template: '<span class="ast-spinner" role="status" aria-label="Loading"></span>',
  styles: [':host { display: inline-block; }'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AstSpinnerComponent {}
