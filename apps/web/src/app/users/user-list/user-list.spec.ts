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
    TestBed.inject(HttpTestingController).expectOne('/api/users').flush(everyone);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  };

  it("draws the admin controls on every row but the admin's own", async () => {
    const el = await render(everyone[0]);
    expect(el.querySelectorAll('li').length).toBe(2);
    expect(el.querySelectorAll('select.role').length).toBe(1);
    expect(el.querySelector('select.role')?.getAttribute('aria-label')).toBe('Role of ada@example.com');
  });

  it('lets a moderator look and nothing more', async () => {
    const el = await render({ ...everyone[1], role: 'MODERATOR' });
    expect(el.querySelectorAll('li').length).toBe(2);
    expect(el.querySelector('select.role')).toBeNull();
    expect(el.querySelector('.row-actions')).toBeNull();
  });
});
