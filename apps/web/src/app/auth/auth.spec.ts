import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { Auth, authInterceptor } from './auth';

describe('authInterceptor', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
  });

  it('sends the token to our private /api only, and drops it on a 401 there', async () => {
    const http = TestBed.inject(HttpClient);
    const backend = TestBed.inject(HttpTestingController);
    const auth = TestBed.inject(Auth);
    auth.token.set('t0k');

    for (const elsewhere of ['/elsewhere/x', '/mcp']) {
      firstValueFrom(http.get(elsewhere)).catch(() => {});
      expect(backend.expectOne(elsewhere).request.headers.has('Authorization')).toBe(false);
    }

    // A public call carries no token, and its 401 -- a wrong password -- is not a dead session.
    auth.login('a@example.com', 'wrong').catch(() => {});
    const login = backend.expectOne('/api/login');
    expect(login.request.headers.has('Authorization')).toBe(false);
    login.flush({ error: 'invalid credentials' }, { status: 401, statusText: 'Unauthorized' });
    await Promise.resolve();
    expect(auth.token()).toBe('t0k');

    const me = firstValueFrom(http.get('/api/users/me')).catch(() => 'failed');
    const req = backend.expectOne('/api/users/me');
    expect(req.request.headers.get('Authorization')).toBe('Bearer t0k');
    req.flush({ error: 'expired' }, { status: 401, statusText: 'Unauthorized' });

    expect(await me).toBe('failed');
    expect(auth.token()).toBeNull();
  });

  it('never shows the previous token\'s user after a sign-in', async () => {
    const backend = TestBed.inject(HttpTestingController);
    const auth = TestBed.inject(Auth);
    auth.token.set('victim');
    const boot = auth.refresh();
    const signedIn = auth.signIn('other');

    const [stale, fresh] = backend.match('/api/users/me');
    expect(stale.request.headers.get('Authorization')).toBe('Bearer victim');
    expect(fresh.request.headers.get('Authorization')).toBe('Bearer other');
    fresh.flush({ id: 2, name: 'Other' });
    await signedIn;
    stale.flush({ id: 1, name: 'Victim' });
    await boot;
    expect(auth.user()?.name).toBe('Other');
  });
});
