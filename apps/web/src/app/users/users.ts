import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { User } from '../auth/auth';

export type Role = User['role'];

/** auth-service's view of everyone: reading is for admins and moderators, every change is the admin's alone. */
@Injectable({ providedIn: 'root' })
export class Users {
  private readonly http = inject(HttpClient);

  list(): Promise<User[]> {
    return firstValueFrom(this.http.get<User[]>('/api/users'));
  }

  /** A changed role also revokes the user's tokens: the role rides inside them. */
  setRole(id: number, role: Role): Promise<User> {
    return firstValueFrom(this.http.put<User>(`/api/users/${id}/role`, { role }));
  }

  /** Suspending revokes too; reactivating lets the user sign in again. */
  setSuspended(id: number, suspended: boolean): Promise<User> {
    return firstValueFrom(this.http.put<User>(`/api/users/${id}/suspended`, { suspended }));
  }

  /** Signs the user out everywhere. It can log straight back in. */
  revoke(id: number): Promise<User> {
    return firstValueFrom(this.http.post<User>(`/api/users/${id}/revoke`, null));
  }
}
