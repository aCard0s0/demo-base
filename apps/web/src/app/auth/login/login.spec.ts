import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { Auth, authInterceptor } from '../auth';
import { Login } from './login';

describe('Login, back from a provider', () => {
  const land = (fragment: string, token?: string) => {
    localStorage.clear();
    if (token) localStorage.setItem('auth-token', token);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { fragment, data: {} } } },
      ],
    });
    login = TestBed.createComponent(Login).componentInstance;
    const backend = TestBed.inject(HttpTestingController);
    backend.expectOne('/api/oauth/providers').flush([]);
    return backend;
  };
  let login: Login;

  it('never adopts a token from the URL: whoever wrote the link would choose the account', () => {
    const backend = land('token=attackers-jwt');
    backend.verify();
    expect(TestBed.inject(Auth).token()).toBeNull();
  });

  it('turns an error code into its own words', () => {
    land('error=cancelled').verify();
    expect(login.error()).toBe('the sign-in was cancelled');
  });

  it('never shows the link\'s own words', () => {
    land('error=Your+account+is+locked%2C+call+555-0100').verify();
    expect(login.error()).toBe('could not sign you in with the provider');
  });

  it('keeps a session it has when there is nothing to collect', async () => {
    const backend = land('oauth=done', 'mine');
    const auth = TestBed.inject(Auth);
    // A forged #oauth=done in a signed-in browser: nothing to collect, and no reason to sign out.
    backend.expectOne({ method: 'POST', url: '/api/oauth/session' })
      .flush({ error: 'no sign-in to finish here, try again' }, { status: 401, statusText: 'Unauthorized' });
    await Promise.resolve();
    expect(auth.token()).toBe('mine');
  });

  it('signs in with what /api/oauth/session hands over', async () => {
    const backend = land('oauth=done');
    backend.expectOne({ method: 'POST', url: '/api/oauth/session' }).flush({ token: 'fresh' });
    await Promise.resolve();
    expect(TestBed.inject(Auth).token()).toBe('fresh');
    expect(backend.expectOne('/api/users/me').request.headers.get('Authorization')).toBe('Bearer fresh');
  });
});
