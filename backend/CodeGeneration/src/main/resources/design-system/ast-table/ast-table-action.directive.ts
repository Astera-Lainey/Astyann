import { Directive, TemplateRef, inject } from '@angular/core';

/**
 * Marks a per-row action template inside an `<ast-table>`:
 *
 * ```html
 * <ast-table [columns]="columns" [rows]="items()">
 *   <button *astTableAction="let row" (click)="edit(row.id)">Edit</button>
 * </ast-table>
 * ```
 *
 * The starred syntax wraps the element in an `<ng-template>`, so the table can stamp it out once
 * per row with that row bound to `let row`. Plain projected content cannot do this: `<ng-content>`
 * is rendered a single time regardless of how many rows there are.
 */
@Directive({
  selector: '[astTableAction]',
  standalone: true
})
export class AstTableActionDirective {
  /**
   * The wrapped element, instantiated per row with `{ $implicit: row }`.
   *
   * <p>The context is typed `any` deliberately: Angular derives the type of `let row` from this
   * generic, and under `strictTemplates` a stricter type such as `unknown` would make every
   * `row.someField` access in the consuming template a compile error.
   */
  readonly template: TemplateRef<{ $implicit: any }> = inject(TemplateRef);
}
