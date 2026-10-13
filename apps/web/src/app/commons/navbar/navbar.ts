import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map } from 'rxjs';
import { Auth } from '../../auth/auth';

interface BreadcrumbItem {
  label: string;
  url: string;
}

/** Breadcrumbs, and the signed-in user (or a sign-in link) on the right. */
@Component({
  selector: 'app-navbar',
  imports: [RouterLink],
  templateUrl: './navbar.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './navbar.scss',
})
export class Navbar {
  private readonly router = inject(Router);
  protected readonly auth = inject(Auth);

  readonly isSidebarOpen = input(false);
  readonly sidebarToggle = output<void>();

  readonly breadcrumbs = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => this.buildBreadcrumbs(event.urlAfterRedirects)),
    ),
    { initialValue: this.buildBreadcrumbs(this.router.url) },
  );

  private buildBreadcrumbs(url: string): BreadcrumbItem[] {
    const segments = url.split(/[?#]/)[0].split('/').filter(Boolean);
    let currentUrl = '';
    return segments.map((segment) => {
      currentUrl += `/${segment}`;
      const label = decodeURIComponent(segment)
        .replace(/[-_]+/g, ' ')
        .replace(/\b\w/g, (char) => char.toUpperCase());
      return { label, url: currentUrl };
    });
  }
}
