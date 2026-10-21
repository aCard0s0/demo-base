import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Auth, User, errorText, leaveWhenSignedOut } from '../../auth/auth';
import { Role, Users } from '../users';

/**
 * `/users`: everyone, for the roles that read everyone. A moderator only looks; an admin creates users, changes
 * roles, suspends and signs users out everywhere -- never itself, which auth-service refuses too, so those controls
 * are not drawn.
 */
@Component({
  selector: 'app-user-list',
  imports: [FormsModule],
  templateUrl: './user-list.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['../../auth/auth.scss', '../../agents/agents.scss'],
})
export class UserList {
  private readonly users = inject(Users);
  private readonly auth = inject(Auth);

  readonly roles: Role[] = ['USER', 'MODERATOR', 'ADMIN'];
  readonly list = signal<User[] | null>(null);
  readonly error = signal('');
  readonly notice = signal('');
  readonly busy = signal(false);
  readonly me = computed(() => this.auth.user()?.id);
  readonly admin = computed(() => this.auth.user()?.role === 'ADMIN');

  /** The new-user form. */
  readonly newName = signal('');
  readonly newEmail = signal('');
  readonly newPassword = signal('');
  readonly newRole = signal<Role>('USER');

  constructor() {
    leaveWhenSignedOut();
    this.users.list().then(
      (list) => this.list.set(list),
      (e) => this.error.set(errorText(e)),
    );
  }

  create(): Promise<void> {
    const email = this.newEmail().trim().toLowerCase();
    return this.run(
      `${email} was created. Hand them their password; they can change it on their profile.`,
      async () => {
        const user = await this.users.create({
          name: this.newName(),
          email,
          password: this.newPassword(),
          role: this.newRole(),
        });
        this.newName.set('');
        this.newEmail.set('');
        this.newPassword.set('');
        this.newRole.set('USER');
        return user;
      },
    );
  }

  setRole(user: User, role: Role): Promise<void> {
    if (role === user.role) return Promise.resolve();
    return this.run(`${user.email} is now ${role}. Their sessions were signed out.`, () =>
      this.users.setRole(user.id, role),
    );
  }

  setSuspended(user: User): Promise<void> {
    const suspend = !user.suspended;
    if (
      suspend &&
      !confirm(`Suspend ${user.email}? They are signed out everywhere and cannot sign in until reactivated.`)
    ) {
      return Promise.resolve();
    }
    return this.run(suspend ? `${user.email} is suspended.` : `${user.email} can sign in again.`, () =>
      this.users.setSuspended(user.id, suspend),
    );
  }

  revoke(user: User): Promise<void> {
    if (!confirm(`Sign ${user.email} out everywhere? They can sign straight back in.`)) return Promise.resolve();
    return this.run(`${user.email} was signed out everywhere.`, () => this.users.revoke(user.id));
  }

  /** An answered user replaces its row, or is appended when new. */
  private async run(done: string, action: () => Promise<User>): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    this.notice.set('');
    try {
      const updated = await action();
      this.list.update((list) =>
        list?.some((u) => u.id === updated.id)
          ? list.map((u) => (u.id === updated.id ? updated : u))
          : [...(list ?? []), updated],
      );
      this.notice.set(done);
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.busy.set(false);
    }
  }
}
