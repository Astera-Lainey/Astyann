import { ChangeDetectionStrategy, Component, ContentChildren, Input, QueryList, TemplateRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AstTableActionDirective } from './ast-table-action.directive';
import { AstBadgeComponent } from '../ast-badge/ast-badge.component';

export type AstBadgeVariant = 'success' | 'warning' | 'danger' | 'info' | 'neutral';

export interface AstTableColumn {
  label: string;
  fieldName: string;
  /** `'badge'` renders the cell as a coloured status pill instead of plain text. */
  kind?: 'text' | 'badge';
  /** Status value → badge variant, used when `kind` is `'badge'`. */
  variants?: Record<string, AstBadgeVariant>;
}

@Component({
  selector: 'ast-table',
  standalone: true,
  imports: [CommonModule, AstBadgeComponent],
  templateUrl: './ast-table.component.html',
  styleUrls: ['./ast-table.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AstTableComponent {
  @Input() columns: AstTableColumn[] = [];
  @Input() rows: any[] = [];

  /** Every `*astTableAction` template supplied by the parent, rendered in each row's last cell. */
  @ContentChildren(AstTableActionDirective) actions?: QueryList<AstTableActionDirective>;

  get actionTemplates(): TemplateRef<{ $implicit: any }>[] {
    return this.actions ? this.actions.toArray().map(action => action.template) : [];
  }

  /** Badge variant for a cell, falling back to `neutral` for values the mapping does not cover. */
  badgeVariant(col: AstTableColumn, row: any): AstBadgeVariant {
    const value = row?.[col.fieldName];
    if (value === null || value === undefined) return 'neutral';
    return col.variants?.[String(value)] ?? 'neutral';
  }
}
