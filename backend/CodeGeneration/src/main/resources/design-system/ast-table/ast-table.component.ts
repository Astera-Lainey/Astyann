import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface AstTableColumn {
  label: string;
  fieldName: string;
}

@Component({
  selector: 'ast-table',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './ast-table.component.html',
  styleUrls: ['./ast-table.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AstTableComponent {
  @Input() columns: AstTableColumn[] = [];
  @Input() rows: any[] = [];
}
