import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Auth, User, errorText, leaveWhenSignedOut } from '../auth';

/** `/profile`: the caller's own details. Every save asks for the current password, unless the user never set one. */
@Component({
  selector: 'app-profile',
  imports: [FormsModule],
  templateUrl: './profile.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../auth.scss',
})
export class Profile {
  private readonly auth = inject(Auth);

  readonly user = this.auth.user;
  readonly name = signal('');
  readonly email = signal('');
  readonly currentPassword = signal('');
  readonly newPassword = signal('');
  readonly error = signal('');
  readonly notice = signal('');
  readonly busy = signal(false);

  constructor() {
    leaveWhenSignedOut();
    // Re-read rather than trusting the cached user: a rename elsewhere, or a role change, shows up here. Shares
    // the boot-time request when a reload lands here.
    this.auth.refresh().then((u) => this.fill(u), (e) => this.error.set(errorText(e)));
  }

  async save(): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    this.notice.set('');
    const before = this.user()!;
    const email = this.email().trim().toLowerCase();
    // A new email is never written straight away. With a password, the save mails the new address a link; a
    // provider-only user asks separately, and its current address approves first.
    const moving = email !== before.email;
    try {
      this.fill(
        await this.auth.update({
          name: this.name(),
          email: before.passwordSet ? email : before.email,
          currentPassword: this.currentPassword(),
          newPassword: this.newPassword(),
        }),
      );
      if (!moving) this.notice.set('Saved.');
      else if (before.passwordSet) this.notice.set(`Saved. Open the link we sent to ${email} to make it your email.`);
      else
        this.notice.set(
          `We sent a link to ${await this.auth.requestEmailChange(email)}. Open it to approve, then confirm from ${email}.`,
        );
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.busy.set(false);
    }
  }

  logout(): void {
    this.auth.logout();
  }

  private fill(u: User): void {
    this.name.set(u.name);
    this.email.set(u.email);
    this.currentPassword.set('');
    this.newPassword.set('');
  }
}
