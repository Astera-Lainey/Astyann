import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Meta, Title } from '@angular/platform-browser';
import { SparkleIconComponent } from '../../shared/components/sparkle-icon/sparkle-icon.component';

/**
 * Public marketing landing page.
 *
 * React source: index.tsx — TanStack Router's `head()` meta config is
 * converted to Angular's `Title`/`Meta` services in `ngOnInit`. The Alata
 * Google Font `<link>` that was injected via `head().links` is instead added
 * globally to `index.html` (see migration notes) since Angular has no
 * per-route equivalent of injecting <link> tags without extra tooling.
 */
@Component({
  selector: 'app-landing-page',
  standalone: true,
  imports: [RouterLink, SparkleIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './landing.component.html',
  styleUrl: './landing.component.scss',
})
export class LandingComponent implements OnInit {
  constructor(
    private readonly title: Title,
    private readonly meta: Meta,
  ) {}

  ngOnInit(): void {
    this.title.setTitle('Astyann — Accelerating Software Delivery Through Automation');
    this.meta.updateTag({
      name: 'description',
      content:
        'Astyann automates critical software engineering activities — from requirement analysis and architecture design to code generation and deployment packaging.',
    });
    this.meta.updateTag({
      property: 'og:title',
      content: 'Astyann — Accelerating Software Delivery',
    });
    this.meta.updateTag({
      property: 'og:description',
      content:
        'From requirement analysis to code generation and deployment — enabling faster, more consistent project delivery.',
    });
  }
}
