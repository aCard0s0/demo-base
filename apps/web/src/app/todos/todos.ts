import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/** todo-service's view of one todo; `owner` is the owning user's id, as a string. */
export interface Todo {
  id: number;
  owner: string;
  title: string;
  done: boolean;
}

/** todo-service: your own todos, or everyone's for an admin or moderator. Only an admin writes others'. */
@Injectable({ providedIn: 'root' })
export class Todos {
  private readonly http = inject(HttpClient);

  list(): Promise<Todo[]> {
    return firstValueFrom(this.http.get<Todo[]>('/api/todos'));
  }

  add(title: string): Promise<Todo> {
    return firstValueFrom(this.http.post<Todo>('/api/todos', { title }));
  }

  /** No body: todo-service flips the flag itself. */
  toggle(id: number): Promise<Todo> {
    return firstValueFrom(this.http.put<Todo>(`/api/todos/${id}`, null));
  }

  remove(id: number): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`/api/todos/${id}`));
  }
}
