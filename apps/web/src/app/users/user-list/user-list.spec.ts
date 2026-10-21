import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Auth, User } from '../../auth/auth';
import { UserList } from './user-list';

describe('UserList', () => {
  const everyone: User[] = [
    {
      id: 1,
      name: 'Root',
      email: 'root@example.com',
      role: 'ADMIN',
      suspended: false,
      passwordSet: true,
    },
    {
      id: 2,
      name: 'Ada',
      email: 'ada@example.com',
      role: 'USER',
      suspended: false,
      passwordSet: true,
    },
  ];

  const render = async (me: User) => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    const auth = TestBed.inject(Auth);
    auth.token.set('t0k');
    auth.user.set(me);
    const fixture = TestBed.createComponent(UserList);
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/users').flush(everyone);
    await fixture.whenStable();
    return { el: fixture.nativeElement as HTMLElement, fixture, http };
  };

  const fill = (list: UserList) => {
    list.newName.set('Grace');
    list.newEmail.set('Grace@Example.com');
    list.newPassword.set('hopper-1906');
    list.newRole.set('MODERATOR');
  };

  it("draws the admin controls on every row but the admin's own", async () => {
    const { el } = await render(everyone[0]);
    expect(el.querySelectorAll('li').length).toBe(2);
    expect(el.querySelectorAll('select.role').length).toBe(1);
    expect(el.querySelector('select.role')?.getAttribute('aria-label')).toBe('Role of ada@example.com');
    expect(el.querySelector('details.new-user')).not.toBeNull();
  });

  it('lets a moderator look and nothing more', async () => {
    const { el } = await render({ ...everyone[1], role: 'MODERATOR' });
    expect(el.querySelectorAll('li').length).toBe(2);
    expect(el.querySelector('select.role')).toBeNull();
    expect(el.querySelector('.row-actions')).toBeNull();
    expect(el.querySelector('details.new-user')).toBeNull();
  });

  it('appends the user an admin creates', async () => {
    const { el, fixture, http } = await render(everyone[0]);
    fill(fixture.componentInstance);
    const created = fixture.componentInstance.create();
    const req = http.expectOne('/api/users/create');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      name: 'Grace',
      email: 'grace@example.com',
      password: 'hopper-1906',
      role: 'MODERATOR',
    });
    req.flush({
      id: 3,
      name: 'Grace',
      email: 'grace@example.com',
      role: 'MODERATOR',
      suspended: false,
      passwordSet: true,
    });
    await created;
    await fixture.whenStable();
    expect(el.querySelectorAll('li').length).toBe(3);
    expect(fixture.componentInstance.newPassword()).toBe('');
    expect(el.querySelector('.auth-ok')?.textContent).toContain('grace@example.com was created');
  });

  it('shows a refused create like any other error and keeps the form', async () => {
    const { el, fixture, http } = await render(everyone[0]);
    fill(fixture.componentInstance);
    const created = fixture.componentInstance.create();
    http
      .expectOne('/api/users/create')
      .flush({ error: 'that email is taken' }, { status: 400, statusText: 'Bad Request' });
    await created;
    await fixture.whenStable();
    expect(el.querySelector('.auth-error')?.textContent).toContain('that email is taken');
    expect(el.querySelectorAll('li').length).toBe(2);
    expect(fixture.componentInstance.newName()).toBe('Grace');
  });
});
