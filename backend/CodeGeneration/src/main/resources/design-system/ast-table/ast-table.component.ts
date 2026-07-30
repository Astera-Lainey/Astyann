import { ChangeDetectionStrategy, Component, ContentChildren, Input, QueryList, TemplateRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AstTableActionDirective } from './ast-table-action.directive';

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

  /** Every `*astTableAction` template supplied by the parent, rendered in each row's last cell. */
  @ContentChildren(AstTableActionDirective) actions?: QueryList<AstTableActionDirective>;

  get actionTemplates(): TemplateRef<{ $implicit: any }>[] {
    return this.actions ? this.actions.toArray().map(action => action.template) : [];
  }
}
