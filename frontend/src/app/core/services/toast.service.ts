import { Injectable, signal } from '@angular/core';

export interface Toast {
  message: string;
  type: 'success' | 'error' | 'info';
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly current = signal<Toast | null>(null);

  private timer: ReturnType<typeof setTimeout> | null = null;

  show(message: string, type: Toast['type'] = 'success', duration = 3000): void {
    if (this.timer) clearTimeout(this.timer);
    this.current.set({ message, type });
    this.timer = setTimeout(() => {
      this.current.set(null);
      this.timer = null;
    }, duration);
  }

  dismiss(): void {
    if (this.timer) clearTimeout(this.timer);
    this.current.set(null);
    this.timer = null;
  }
}
