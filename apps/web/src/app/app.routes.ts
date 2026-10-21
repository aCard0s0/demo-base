import { Routes } from '@angular/router';
import { authGuard } from './auth/auth';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./auth/login/login').then((m) => m.Login) },
  { path: 'signup', loadComponent: () => import('./auth/login/login').then((m) => m.Login), data: { signup: true } },
  // The link a provider-only user's current address is mailed; holding it is the proof, so no guard.
  { path: 'confirm-email', loadComponent: () => import('./auth/confirm-email/confirm-email').then((m) => m.ConfirmEmail) },
  { path: 'profile', canActivate: [authGuard], loadComponent: () => import('./auth/profile/profile').then((m) => m.Profile) },
  // Admins and moderators; anyone else gets auth-service's 403 on the page.
  { path: 'users', canActivate: [authGuard], loadComponent: () => import('./users/user-list/user-list').then((m) => m.UserList) },
  { path: 'todos', canActivate: [authGuard], loadComponent: () => import('./todos/todo-list/todo-list').then((m) => m.TodoList) },
  { path: 'agents', canActivate: [authGuard], loadComponent: () => import('./agents/agent-list/agent-list').then((m) => m.AgentList) },
  {
    path: 'agents/:id',
    canActivate: [authGuard],
    loadComponent: () => import('./agents/agent-detail/agent-detail').then((m) => m.AgentDetail),
  },
  { path: '**', redirectTo: 'agents' },
];
