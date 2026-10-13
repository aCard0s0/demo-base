import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { errorText, leaveWhenSignedOut } from '../../auth/auth';
import { ACTIVITY_KINDS, Activity, Agent, Agents, OTHERS_ACCESS, OthersAccess, activityBadge } from '../agents';

type Tab = 'list' | 'new' | 'activity';

/** An activity line with the agent it belongs to, for the search across all of them. */
type AgentActivity = Activity & { agentId: number; agentName: string };

/**
 * `/agents`: three tabs. The caller's agents; a form to make one, which starts with no servers, on its own page;
 * and a search over the activity of every agent.
 */
@Component({
  selector: 'app-agent-list',
  imports: [FormsModule, RouterLink, DatePipe],
  templateUrl: './agent-list.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['../../auth/auth.scss', '../agents.scss'],
})
export class AgentList {
  private readonly agents = inject(Agents);
  private readonly router = inject(Router);

  readonly tab = signal<Tab>('list');
  readonly list = signal<Agent[] | null>(null);
  readonly name = signal('');
  readonly instructions = signal('');
  readonly othersAccessChoices = OTHERS_ACCESS;
  readonly othersAccess = signal<OthersAccess>('NONE');
  readonly error = signal('');
  readonly busy = signal(false);

  readonly activity = signal<AgentActivity[] | null>(null);
  readonly kinds = ACTIVITY_KINDS;
  readonly badge = activityBadge;
  /** Filters: an agent id ('' for all), a kind ('' for all) and free text over the agent name, kind and detail. */
  readonly agentFilter = signal('');
  readonly kind = signal('');
  readonly query = signal('');
  readonly shown = computed(() => {
    const q = this.query().trim().toLowerCase();
    return (this.activity() ?? []).filter(
      (l) =>
        (!this.agentFilter() || String(l.agentId) === this.agentFilter()) &&
        (!this.kind() || l.kind === this.kind()) &&
        (!q || `${l.agentName} ${l.kind} ${l.detail}`.toLowerCase().includes(q)),
    );
  });

  constructor() {
    leaveWhenSignedOut();
    this.agents.list().then(
      (list) => this.list.set(list),
      (e) => this.error.set(errorText(e)),
    );
  }

  open(tab: Tab): void {
    this.tab.set(tab);
    if (tab === 'activity' && !this.activity()) this.loadActivity();
  }

  // ponytail: one request per agent, each its newest 100 lines; a server-side search endpoint if owners keep many agents.
  async loadActivity(): Promise<void> {
    this.error.set('');
    try {
      const agents = await this.agents.list();
      this.list.set(agents);
      const logs = await Promise.all(
        agents.map(async (a) => (await this.agents.activity(a.id)).map((l) => ({ ...l, agentId: a.id, agentName: a.name }))),
      );
      this.activity.set(logs.flat().sort((x, y) => Date.parse(y.at) - Date.parse(x.at)));
    } catch (e) {
      this.error.set(errorText(e));
    }
  }

  async create(): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    try {
      const agent = await this.agents.create({
        name: this.name(),
        instructions: this.instructions(),
        othersAccess: this.othersAccess(),
      });
      await this.router.navigate(['/agents', agent.id]);
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.busy.set(false);
    }
  }
}
