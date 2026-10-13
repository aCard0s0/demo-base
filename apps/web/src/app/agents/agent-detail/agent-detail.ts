import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { errorText, leaveWhenSignedOut } from '../../auth/auth';
import { ACTIVITY_KINDS, Activity, Agent, Agents, McpServer, OTHERS_ACCESS, OthersAccess, activityBadge } from '../agents';
import { AgentToken } from '../agent-token/agent-token';
import { ServerForm } from '../server-form/server-form';

/** `/agents/:id`: one agent's settings, its servers, its tokens and what it has been doing. */
@Component({
  selector: 'app-agent-detail',
  imports: [FormsModule, RouterLink, DatePipe, AgentToken, ServerForm],
  templateUrl: './agent-detail.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['../../auth/auth.scss', '../agents.scss'],
})
export class AgentDetail {
  private readonly agents = inject(Agents);
  private readonly router = inject(Router);

  readonly id = Number(inject(ActivatedRoute).snapshot.paramMap.get('id'));
  readonly agent = signal<Agent | null>(null);
  readonly activity = signal<Activity[]>([]);
  /** The activity kinds as a filter; '' is all of them. */
  readonly kinds = ACTIVITY_KINDS;
  readonly kind = signal('');
  readonly shown = computed(() => (this.kind() ? this.activity().filter((l) => l.kind === this.kind()) : this.activity()));
  readonly name = signal('');
  readonly instructions = signal('');
  readonly othersAccessChoices = OTHERS_ACCESS;
  readonly othersAccess = signal<OthersAccess>('NONE');
  /** The id of the server being edited, 'new' for the add form, or null for neither. By id: a reload replaces the rows. */
  readonly editing = signal<number | 'new' | null>(null);
  readonly error = signal('');
  readonly notice = signal('');
  readonly busy = signal(false);

  constructor() {
    leaveWhenSignedOut();
    if (!Number.isInteger(this.id)) {
      this.error.set('no such agent');
      return;
    }
    this.agents.get(this.id).then(
      (a) => this.fill(a),
      (e) => this.error.set(errorText(e)),
    );
    this.loadActivity();
  }

  save(): Promise<void> {
    return this.run(async () => {
      const agent = await this.agents.update(this.id, {
        name: this.name(),
        instructions: this.instructions(),
        othersAccess: this.othersAccess(),
      });
      this.fill(agent);
      this.loadActivity();
      this.notice.set('Saved.');
    });
  }

  serverSaved(saved: McpServer): void {
    this.editing.set(null);
    this.servers((list) => (list.some((s) => s.id === saved.id) ? list.map((s) => (s.id === saved.id ? saved : s)) : [...list, saved]));
  }

  removeServer(server: McpServer): Promise<void> {
    if (!confirm(`Remove server "${server.name}" from this agent?`)) return Promise.resolve();
    return this.run(async () => {
      await this.agents.removeServer(this.id, server.id);
      this.servers((list) => list.filter((s) => s.id !== server.id));
    });
  }

  delete(): Promise<void> {
    if (!confirm(`Delete agent "${this.agent()?.name}" and its activity? Its tokens stop working. This cannot be undone.`)) {
      return Promise.resolve();
    }
    return this.run(async () => {
      await this.agents.delete(this.id);
      await this.router.navigateByUrl('/agents');
    });
  }

  readonly badge = activityBadge;

  loadActivity(): void {
    this.agents.activity(this.id).then(
      (list) => this.activity.set(list),
      () => {}, // the agent's own load says what went wrong
    );
  }

  /** Servers only, from what the API answered: the settings form keeps whatever is being typed into it. */
  private servers(change: (list: McpServer[]) => McpServer[]): void {
    const agent = this.agent();
    if (agent) this.agent.set({ ...agent, servers: change(agent.servers) });
    this.loadActivity();
  }

  private fill(agent: Agent): void {
    this.agent.set(agent);
    this.name.set(agent.name);
    this.instructions.set(agent.instructions);
    this.othersAccess.set(agent.othersAccess);
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    this.notice.set('');
    try {
      await action();
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.busy.set(false);
    }
  }
}
