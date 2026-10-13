import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { errorText } from '../../auth/auth';
import { Agents } from '../agents';

/**
 * Minting and revoking the agent's own tokens. A minted token lives in this component's signal and nowhere else
 * -- not localStorage, not the URL, not a log -- until "Done" or leaving the page drops it. agent-service keeps
 * no copy either, so a lost one is replaced, not recovered.
 */
@Component({
  selector: 'app-agent-token',
  templateUrl: './agent-token.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['../../auth/auth.scss', '../agents.scss'],
})
export class AgentToken {
  private readonly agents = inject(Agents);

  readonly agentId = input.required<number>();
  readonly agentName = input.required<string>();
  /** Something was logged on the agent: the page re-reads its activity. */
  readonly changed = output<void>();

  readonly token = signal('');
  /** Which of the two copy buttons just worked: 'token' or 'command'. */
  readonly copied = signal('');
  readonly error = signal('');
  readonly notice = signal('');
  readonly busy = signal(false);

  readonly mcpUrl = computed(() => `${location.origin}/mcp?agent=${this.agentId()}`);

  /** The README's one-liner for Claude Code, with the freshly minted token in it, or a placeholder until there is one. */
  readonly claudeCommand = computed(() => {
    const name = this.agentName().toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'agent';
    return `claude mcp add --transport http ${name} "${this.mcpUrl()}" --header "Authorization: Bearer ${this.token() || '<agent token>'}"`;
  });

  mint(): Promise<void> {
    return this.run(async () => {
      this.token.set(await this.agents.mintToken(this.agentId()));
    });
  }

  revoke(): Promise<void> {
    const sure = confirm(
      `Revoke every token made for "${this.agentName()}"? Clients using them are cut off within ten seconds. ` +
        'Your own sign-in and your other agents are not affected.',
    );
    if (!sure) return Promise.resolve();
    return this.run(async () => {
      await this.agents.revokeTokens(this.agentId());
      this.token.set('');
      this.notice.set('Revoked. Every token made for this agent stops working within ten seconds.');
    });
  }

  async copy(what: 'token' | 'command'): Promise<void> {
    try {
      await navigator.clipboard.writeText(what === 'token' ? this.token() : this.claudeCommand());
      this.copied.set(what);
    } catch {
      this.error.set('Could not copy: select the text and copy it by hand.');
    }
  }

  done(): void {
    this.token.set('');
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    this.notice.set('');
    this.copied.set('');
    try {
      await action();
      this.changed.emit();
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.busy.set(false);
    }
  }
}
