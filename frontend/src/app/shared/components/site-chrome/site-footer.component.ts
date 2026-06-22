import { ChangeDetectionStrategy, Component } from '@angular/core';
import { LogoComponent } from '../logo/logo.component';

interface FooterColumn {
  title: string;
  links: string[];
}

/**
 * 4-column site footer: brand blurb + Platform/Institution/Resources link
 * columns, plus a bottom bar with copyright and build version.
 * Direct conversion of `SiteFooter` from SiteChrome.tsx.
 */
@Component({
  selector: 'app-site-footer',
  standalone: true,
  imports: [LogoComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './site-footer.component.html',
  styleUrl: './site-chrome.component.scss',
})
export class SiteFooterComponent {
  readonly columns: FooterColumn[] = [
    { title: 'Platform', links: ['Features', 'Workspace', 'Changelog'] },
    { title: 'Institution', links: ['About', 'Contact', 'Support'] },
    { title: 'Resources', links: ['Docs', 'Guides', 'Status'] },
  ];
}
