import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { Auth, errorText } from '../auth';

/** What a provider sign-in's `#error=<code>` means. The words live here so a link cannot put its own in our alert. */
const OAUTH_ERRORS: Record<string, string> = {
  cancelled: 'the sign-in was cancelled',
  'not-started': 'that sign-in did not start here, try again',
  'no-code': 'the provider sent no authorization code',
  suspended: 'this user is suspended',
  failed: 'could not sign you in with the provider',
};

/**
 * `/login` and `/signup`: email and password, or a provider button. Providers always land back on `/login`, and
 * so does the signup link, as `/login#verify=...`, which asks for the password chosen at signup.
 */
@Component({
  selector: 'app-login',
  imports: [FormsModule, RouterLink],
  templateUrl: './login.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../auth.scss',
})
export class Login {
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly signup = this.route.snapshot.data['signup'] === true;
  // No providers (or auth-service down) just means no buttons.
  readonly providers = toSignal(this.auth.providers().pipe(catchError(() => of([]))), { initialValue: [] });
  /** Each provider key auth-service knows, to its bootstrap-icons glyph; an unknown one gets a plain sign-in arrow. */
  readonly icons: Record<string, string> = {
    google: 'google',
    github: 'github',
    microsoft: 'microsoft',
    apple: 'apple',
    x: 'twitter-x',
    linkedin: 'linkedin',
    discord: 'discord',
  };

  readonly name = signal('');
  readonly email = signal('');
  readonly password = signal('');
  readonly error = signal('');
  readonly notice = signal('');
  readonly busy = signal(false);
  /** The signup link's token: the form then only asks for the password. */
  readonly verifyToken: string | null;

  constructor() {
    // A provider sign-in comes back as /login#oauth=done or /login#error=...; the token itself waits in a cookie
    // (Auth.finishOAuth). A #token is never taken: whoever wrote the link would choose the account.
    const result = new URLSearchParams(this.route.snapshot.fragment ?? '');
    const oauth = result.get('oauth') === 'done';
    this.verifyToken = result.get('verify');
    // history.state kept: it holds the router's navigation id.
    if (oauth || this.verifyToken || result.has('error')) history.replaceState(history.state, '', location.pathname);
    if (oauth) this.run(() => this.auth.finishOAuth());
    else if (result.has('error')) this.error.set(OAUTH_ERRORS[result.get('error')!] ?? OAUTH_ERRORS['failed']);
  }

  submit(): void {
    if (this.verifyToken) this.run(() => this.auth.verifyEmail(this.verifyToken!, this.password()));
    else if (!this.signup) this.run(() => this.auth.login(this.email(), this.password()));
    else
      this.run(async () => {
        await this.auth.register(this.name(), this.email(), this.password());
        this.notice.set(`We sent a link to ${this.email()}. Open it to finish creating your account.`);
        return false;
      });
  }

  /** `action` resolving to false stays on the page; anything else signed in and goes home. */
  private async run(action: () => Promise<unknown>): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    this.notice.set('');
    try {
      if ((await action()) === false) return;
      await this.router.navigateByUrl('/');
    } catch (e) {
      // A failed attempt leaves any session already held alone; a dead token is the interceptor's to drop.
      this.error.set(errorText(e));
    } finally {
      this.busy.set(false);
    }
  }
}
