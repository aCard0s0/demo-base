import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

export type Access = 'READ' | 'WRITE';
export type OthersAccess = 'NONE' | Access;

/** The choices both agent forms offer, in order. */
export const OTHERS_ACCESS: { value: OthersAccess; label: string }[] = [
  { value: 'NONE', label: 'None' },
  { value: 'READ', label: 'Read their setup and activity' },
  { value: 'WRITE', label: 'Read and change them' },
];

/** A server row as agent-service answers it. The stored header never comes back, only whether there is one. */
export interface McpServer {
  id: number;
  name: string;
  url: string;
  access: Access;
  forwardCallerToken: boolean;
  hasAuthHeader: boolean;
  /** One of the deployment's own servers: READ keys on its annotations, and it may be forwarded the caller's token. */
  trusted: boolean;
  readOnlyTools: string[];
}

export interface Agent {
  id: number;
  name: string;
  instructions: string;
  othersAccess: OthersAccess;
  servers: McpServer[];
}

export interface Activity {
  id: number;
  at: string;
  kind: string;
  detail: string;
}

/** The activity kinds agent-service writes, for the filters. */
export const ACTIVITY_KINDS = ['connected', 'tool_call', 'tool_denied', 'config_changed'];

/** A refusal stands out; a tool that ran but failed is a warning; the rest is plain. */
export function activityBadge(line: Activity): string {
  if (line.kind === 'tool_denied') return 'text-bg-danger';
  if (line.kind === 'tool_call' && line.detail.includes('-> error')) return 'text-bg-warning';
  return 'text-bg-secondary';
}

export type AgentEdit = Pick<Agent, 'name' | 'instructions' | 'othersAccess'>;

/** The server form as typed. `authHeader` is only ever a new value: a stored one is never shown or sent back. */
export interface ServerForm {
  name: string;
  url: string;
  access: Access;
  forwardCallerToken: boolean;
  authHeader: string;
  clearAuthHeader: boolean;
  /** Comma or space separated. */
  readOnlyTools: string;
}

/**
 * The body for adding a server, or editing `existing`. The header is write-only: blank leaves the stored one
 * alone, and only an explicit clear sends the empty string that removes it. The caller's token may only be
 * forwarded to the deployment's own servers, which a row only says once saved, so the flag is sent only when
 * editing a trusted row or one that forwards already (to turn it off). A new row leaves it unsaid, and agent-service
 * forwards to its own servers unless a header was given.
 */
export function serverBody(form: ServerForm, existing?: McpServer): Record<string, unknown> {
  const body: Record<string, unknown> = {
    name: form.name.trim(),
    url: form.url.trim(),
    access: form.access,
    readOnlyTools: form.readOnlyTools.split(/[\s,]+/).filter(Boolean),
  };
  if (form.clearAuthHeader) body['authHeader'] = '';
  else if (form.authHeader.trim()) body['authHeader'] = form.authHeader.trim();
  if (existing && (existing.trusted || existing.forwardCallerToken)) body['forwardCallerToken'] = form.forwardCallerToken;
  return body;
}

/** agent-service's `/api/agents`, the caller's own agents only. The bearer is the interceptor's to add. */
@Injectable({ providedIn: 'root' })
export class Agents {
  private readonly http = inject(HttpClient);

  list(): Promise<Agent[]> {
    return firstValueFrom(this.http.get<Agent[]>('/api/agents'));
  }

  get(id: number): Promise<Agent> {
    return firstValueFrom(this.http.get<Agent>(`/api/agents/${id}`));
  }

  create(agent: AgentEdit): Promise<Agent> {
    return firstValueFrom(this.http.post<Agent>('/api/agents', agent));
  }

  update(id: number, agent: AgentEdit): Promise<Agent> {
    return firstValueFrom(this.http.patch<Agent>(`/api/agents/${id}`, agent));
  }

  delete(id: number): Promise<unknown> {
    return firstValueFrom(this.http.delete(`/api/agents/${id}`));
  }

  addServer(id: number, form: ServerForm): Promise<McpServer> {
    return firstValueFrom(this.http.post<McpServer>(`/api/agents/${id}/servers`, serverBody(form)));
  }

  updateServer(id: number, server: McpServer, form: ServerForm): Promise<McpServer> {
    return firstValueFrom(this.http.patch<McpServer>(`/api/agents/${id}/servers/${server.id}`, serverBody(form, server)));
  }

  removeServer(id: number, serverId: number): Promise<unknown> {
    return firstValueFrom(this.http.delete(`/api/agents/${id}/servers/${serverId}`));
  }

  activity(id: number): Promise<Activity[]> {
    return firstValueFrom(this.http.get<Activity[]>(`/api/agents/${id}/activity`));
  }

  /** A 30-day token for this agent alone, shown once: agent-service keeps no copy, and neither may we. */
  async mintToken(id: number): Promise<string> {
    return (await firstValueFrom(this.http.post<{ token: string }>(`/api/agents/${id}/token`, null))).token;
  }

  revokeTokens(id: number): Promise<unknown> {
    return firstValueFrom(this.http.post(`/api/agents/${id}/token/revoke`, null));
  }
}
