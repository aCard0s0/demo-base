import { ChangeDetectionStrategy, Component, computed, inject, model } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { Auth } from '../../auth/auth';

/** A flat menu, dark theme only (data-theme in index.html). */
@Component({
  selector: 'app-sidebar',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './sidebar.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './sidebar.scss',
})
export class Sidebar {
  private readonly auth = inject(Auth);

  readonly isExpanded = model(false);

  /** Users only for the roles auth-service lets read everyone; a USER would only get its 403. */
  readonly menuItems = computed(() => {
    const role = this.auth.user()?.role;
    return [
      { label: 'Todos', icon: 'bi-check2-square', route: '/todos' },
      { label: 'Agents', icon: 'bi-robot', route: '/agents' },
      ...(role === 'ADMIN' || role === 'MODERATOR' ? [{ label: 'Users', icon: 'bi-people', route: '/users' }] : []),
    ];
  });

  toggleSidebar(): void {
    this.isExpanded.set(!this.isExpanded());
  }

  onItemClick(): void {
    // On mobile, collapse sidebar after navigation
    if (window.innerWidth < 768) this.isExpanded.set(false);
  }
}
