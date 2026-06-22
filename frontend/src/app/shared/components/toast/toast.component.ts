import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  selector: 'app-toast',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (toastService.current(); as toast) {
      <div class="toast toast--{{ toast.type }}" role="alert">
        {{ toast.message }}
        <button type="button" class="toast__close" (click)="toastService.dismiss()">&times;</button>
      </div>
    }
  `,
  styles: [`
    :host { position: fixed; top: 1rem; right: 1rem; z-index: 9999; }
    .toast {
      display: flex; align-items: center; gap: 0.75rem;
      padding: 0.75rem 1rem; border-radius: 0.5rem;
      color: #fff; font-size: 0.9rem; font-weight: 500;
      box-shadow: 0 4px 12px rgba(0,0,0,0.15);
      animation: slideIn 0.3s ease;
    }
    .toast--success { background: #16a34a; }
    .toast--error   { background: #dc2626; }
    .toast--info    { background: #2563eb; }
    .toast__close {
      background: none; border: none; color: inherit;
      font-size: 1.25rem; cursor: pointer; line-height: 1; padding: 0;
      opacity: 0.8;
    }
    .toast__close:hover { opacity: 1; }
    @keyframes slideIn {
      from { transform: translateX(100%); opacity: 0; }
      to   { transform: translateX(0);    opacity: 1; }
    }
  `],
})
export class ToastComponent {
  readonly toastService = inject(ToastService);
}
