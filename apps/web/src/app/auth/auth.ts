import { HttpClient, HttpContext, HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { Injectable, effect, inject, signal } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, firstValueFrom, throwError } from 'rxjs';

/** The caller as auth-service's `GET /api/users/me` answers it. */
export interface User {
  id: number;
  name: string;
  email: string;
  role: 'ADMIN' | 'MODERATOR' | 'USER';
  suspended: boolean;
  /** False for a user made by signing in with a provider: it may rename, nothing else. */
  passwordSet: boolean;
}

export interface Provider {
  key: string;
  label: string;
}

export interface ProfileEdit {
  name: string;
  email: string;
  currentPassword: string;
  newPassword: string;
}

const TOKEN_KEY = 'auth-token';

/** Marks a call to an endpoint that takes no token: {@link authInterceptor} sends it none and drops none for it. */
const PUBLIC = new HttpContextToken(() => false);
const anonymous = () => ({ context: new HttpContext().set(PUBLIC, true) });

/**
 * The signed-in user. The token is auth-service's own JWT, kept in localStorage and sent as a bearer on every
 * `/api` call by {@link authInterceptor}; signing out is dropping it, since a signed token cannot be recalled.
 */
@Injectable({ providedIn: 'root' })
export class Auth {
  private readonly http = inject(HttpClient);

  readonly token = signal(localStorage.getItem(TOKEN_KEY));
  readonly user = signal<User | null>(null);

  /** The /me in flight, so the boot-time load and a page's own share one request. */
  private loading?: Promise<User>;

  constructor() {
    effect(() => {
      const token = this.token();
      if (token) localStorage.setItem(TOKEN_KEY, token);
      else localStorage.removeItem(TOKEN_KEY);
    });
  }

  providers() {
    return this.http.get<Provider[]>('/api/oauth/providers', anonymous());
  }

  async login(email: string, password: string): Promise<void> {
    const res = await firstValueFrom(this.http.post<{ token: string }>('/api/login', { email, password }, anonymous()));
    await this.signIn(res.token);
  }

  /** Registration signs nobody in: the account waits for the link mailed to its address. */
  async register(name: string, email: string, password: string): Promise<void> {
    await firstValueFrom(this.http.post('/api/users', { name, email, password }, anonymous()));
  }

  /** Spends the signup link with the password chosen at signup; the answer is a login. */
  async verifyEmail(token: string, password: string): Promise<void> {
    const res = await firstValueFrom(this.http.post<{ token: string }>('/api/login/verify-email', { token, password }, anonymous()));
    await this.signIn(res.token);
  }

  /**
   * Finishes a provider sign-in: the callback left the token in an HttpOnly cookie on this browser, never in the
   * URL, so a link cannot sign anybody into someone else's account. The cookie is spent by this call.
   */
  async finishOAuth(): Promise<void> {
    const res = await firstValueFrom(this.http.post<{ token: string }>('/api/oauth/session', null, anonymous()));
    await this.signIn(res.token);
  }

  /** Adopts a token: a password login, a verified signup link, or a finished provider sign-in. */
  async signIn(token: string): Promise<User> {
    this.token.set(token);
    // A /me still in flight was asked with the previous token: not this user's, so not one to share.
    this.loading = undefined;
    return this.refresh();
  }

  refresh(): Promise<User> {
    if (this.loading) return this.loading;
    const token = this.token();
    const loading: Promise<User> = firstValueFrom(this.http.get<User>('/api/users/me'))
      .then((user) => {
        // A sign-in in between made this someone else's answer; the newer request sets the user.
        if (this.token() === token) this.user.set(user);
        return user;
      })
      .finally(() => {
        if (this.loading === loading) this.loading = undefined;
      });
    return (this.loading = loading);
  }

  /**
   * A new password revokes every token the user held, so the answer carries a fresh one to swap in. A user
   * without a password gets none back (nothing was checked), and keeps the token it has.
   */
  async update(edit: ProfileEdit): Promise<User> {
    const { token, ...user } = await firstValueFrom(this.http.put<User & { token?: string }>('/api/users/me', edit));
    if (token) this.token.set(token);
    this.user.set(user);
    return user;
  }

  /** A provider-only user's new email. Resolves to the current address, where the confirmation link went. */
  async requestEmailChange(email: string): Promise<string> {
    return (await firstValueFrom(this.http.post<{ email: string }>('/api/users/me/email', { email }))).email;
  }

  /**
   * Spends a change-of-email link: `changed` with the address now held, or not yet, with the new address the
   * next link went to (a provider-only user's old inbox approves first).
   */
  async confirmEmailChange(token: string): Promise<{ email: string; changed: boolean }> {
    const step = await firstValueFrom(
      this.http.post<{ email: string; changed: boolean }>('/api/users/email-confirm', { token }, anonymous()),
    );
    if (step.changed && this.user()) await this.refresh();
    return step;
  }

  logout(): void {
    this.token.set(null);
    this.user.set(null);
  }
}

/** auth-service's `{error}` body, or a generic line when there is none (proxy down, network). */
export function errorText(e: unknown): string {
  return (e instanceof HttpErrorResponse && e.error?.error) || 'something went wrong, try again';
}

/**
 * Bearer on every `/api` call of ours; a 401 means the token died (expiry, revoke, restart), so drop it.
 * Dropping is all: a page that needs a user watches the token and leaves. A call marked public never sees the token
 * (a login, a mailed link, the OAuth hand-off): its 401 is a wrong password or a sign-in never started here,
 * not a dead session.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(Auth);
  const token = auth.token();
  if (!token || !req.url.startsWith('/api/') || req.context.get(PUBLIC)) return next(req);
  return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })).pipe(
    catchError((e) => {
      if (e instanceof HttpErrorResponse && e.status === 401) auth.logout();
      return throwError(() => e);
    }),
  );
};

export const authGuard: CanActivateFn = () => (inject(Auth).token() ? true : inject(Router).parseUrl('/login'));

/**
 * For a page behind {@link authGuard}: leaves for /login the moment the token goes, whether by signing out here or
 * by a 401 under one of the page's own requests. Called from the constructor, like any effect.
 */
export function leaveWhenSignedOut(): void {
  const auth = inject(Auth);
  const router = inject(Router);
  effect(() => {
    if (!auth.token()) router.navigateByUrl('/login');
  });
}
