import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Auth, errorText, leaveWhenSignedOut } from '../../auth/auth';
import { Todo, Todos } from '../todos';

/**
 * `/todos`: the caller's todos, or everyone's for an admin or moderator. Only an admin may change someone else's
 * (a moderator gets todo-service's 404), so others' rows carry no controls unless you are one.
 */
@Component({
  selector: 'app-todo-list',
  imports: [FormsModule],
  templateUrl: './todo-list.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['../../auth/auth.scss', '../../agents/agents.scss'],
})
export class TodoList {
  private readonly todos = inject(Todos);
  private readonly auth = inject(Auth);

  readonly list = signal<Todo[] | null>(null);
  readonly error = signal('');
  readonly busy = signal(false);
  readonly newTitle = signal('');
  readonly me = computed(() => String(this.auth.user()?.id));
  readonly admin = computed(() => this.auth.user()?.role === 'ADMIN');

  constructor() {
    leaveWhenSignedOut();
    this.todos.list().then(
      (list) => this.list.set(list),
      (e) => this.error.set(errorText(e)),
    );
  }

  writable(todo: Todo): boolean {
    return this.admin() || todo.owner === this.me();
  }

  add(): Promise<void> {
    return this.run(async () => {
      const todo = await this.todos.add(this.newTitle());
      this.newTitle.set('');
      this.list.update((list) => [...(list ?? []), todo]);
    });
  }

  toggle(todo: Todo): Promise<void> {
    return this.run(async () => {
      const updated = await this.todos.toggle(todo.id);
      this.list.update((list) => list?.map((t) => (t.id === updated.id ? updated : t)) ?? null);
    });
  }

  remove(todo: Todo): Promise<void> {
    return this.run(async () => {
      await this.todos.remove(todo.id);
      this.list.update((list) => list?.filter((t) => t.id !== todo.id) ?? null);
    });
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    try {
      await action();
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.busy.set(false);
    }
  }
}
