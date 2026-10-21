import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Auth, User } from '../../auth/auth';
import { Todo } from '../todos';
import { TodoList } from './todo-list';

describe('TodoList', () => {
  const ada: User = { id: 2, name: 'Ada', email: 'ada@example.com', role: 'USER', suspended: false, passwordSet: true };
  const todos: Todo[] = [
    { id: 1, owner: '2', title: 'mine', done: false },
    { id: 2, owner: '9', title: 'theirs', done: true },
  ];

  const render = async (me: User) => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    const auth = TestBed.inject(Auth);
    auth.token.set('t0k');
    auth.user.set(me);
    const fixture = TestBed.createComponent(TodoList);
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/todos').flush(todos);
    await fixture.whenStable();
    return { el: fixture.nativeElement as HTMLElement, fixture, http };
  };

  it("draws no controls on someone else's todo for a moderator", async () => {
    const { el } = await render({ ...ada, role: 'MODERATOR' });
    expect(el.querySelectorAll('li').length).toBe(2);
    expect(el.querySelectorAll('li button').length).toBe(1);
    expect(el.querySelectorAll('input[type=checkbox]:disabled').length).toBe(1);
  });

  it('lets an admin change anyone\'s', async () => {
    const { el } = await render({ ...ada, role: 'ADMIN' });
    expect(el.querySelectorAll('li button').length).toBe(2);
  });

  it('adds, toggles and deletes', async () => {
    const { el, fixture, http } = await render(ada);
    const list = fixture.componentInstance;

    list.newTitle.set('new');
    const added = list.add();
    const post = http.expectOne('/api/todos');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({ title: 'new' });
    post.flush({ id: 3, owner: '2', title: 'new', done: false });
    await added;

    const toggled = list.toggle(todos[0]);
    const put = http.expectOne('/api/todos/1');
    expect(put.request.method).toBe('PUT');
    put.flush({ ...todos[0], done: true });
    await toggled;

    const removed = list.remove(todos[1]);
    http.expectOne({ method: 'DELETE', url: '/api/todos/2' }).flush(null);
    await removed;

    await fixture.whenStable();
    expect(list.list()?.map((t) => [t.id, t.done])).toEqual([
      [1, true],
      [3, false],
    ]);
    expect(list.newTitle()).toBe('');
    expect(el.querySelectorAll('li').length).toBe(2);
  });

  it('shows a refused add', async () => {
    const { el, fixture, http } = await render(ada);
    const added = fixture.componentInstance.add();
    http.expectOne('/api/todos').flush({ error: 'title is required' }, { status: 400, statusText: 'Bad Request' });
    await added;
    await fixture.whenStable();
    expect(el.querySelector('.auth-error')?.textContent).toContain('title is required');
  });
});
