import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'ast-button',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './ast-button.component.html',
  styleUrls: ['./ast-button.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AstButtonComponent {
  @Input() variant: 'primary' | 'secondary' | 'danger' | 'ghost' = 'primary';
  @Input() size: 'sm' | 'md' | 'lg' = 'md';
  @Input() type: 'button' | 'submit' | 'reset' = 'button';
  @Input() disabled = false;
  @Input() loading = false;
}
