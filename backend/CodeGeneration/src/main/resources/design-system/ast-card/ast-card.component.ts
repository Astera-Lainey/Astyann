import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'ast-card',
  standalone: true,
  templateUrl: './ast-card.component.html',
  styleUrls: ['./ast-card.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AstCardComponent {}
