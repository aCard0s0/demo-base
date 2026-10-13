import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Auth, errorText } from '../auth';

/**
 * `/confirm-email#token=...`, opened from a change-of-email mail: the approval a provider-only user's current
 * address gets, or the confirmation the new address gets. A button rather than confirming on load: mail scanners
 * open links on their own, and must not be what approves the change.
 */
@Component({
  selector: 'app-confirm-email',
  imports: [RouterLink],
  templateUrl: './confirm-email.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../auth.scss',
})
export class ConfirmEmail {
  private readonly auth = inject(Auth);

  private readonly token = new URLSearchParams(inject(ActivatedRoute).snapshot.fragment ?? '').get('token');
  readonly missing = !this.token;
  readonly done = signal<{ email: string; changed: boolean } | null>(null);
  readonly error = signal('');
  readonly busy = signal(false);

  constructor() {
    // Out of the address bar and the history, like the sign-in token.
    if (this.token) history.replaceState(history.state, '', location.pathname);
  }

  async confirm(): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    try {
      this.done.set(await this.auth.confirmEmailChange(this.token!));
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.busy.set(false);
    }
  }
}
