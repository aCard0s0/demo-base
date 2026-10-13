import { ChangeDetectionStrategy, Component, inject, input, linkedSignal, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { errorText } from '../../auth/auth';
import { Access, Agents, McpServer } from '../agents';

/** Adds a server to an agent, or edits `server`. The auth header is write-only: typed here, never shown back. */
@Component({
  selector: 'app-server-form',
  imports: [FormsModule],
  templateUrl: './server-form.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['../../auth/auth.scss', '../agents.scss'],
})
export class ServerForm {
  private readonly agents = inject(Agents);

  readonly agentId = input.required<number>();
  readonly server = input<McpServer>();
  readonly saved = output<McpServer>();
  readonly cancelled = output<void>();

  readonly name = linkedSignal(() => this.server()?.name ?? '');
  readonly url = linkedSignal(() => this.server()?.url ?? '');
  readonly access = linkedSignal<Access>(() => this.server()?.access ?? 'READ');
  readonly forwardCallerToken = linkedSignal(() => this.server()?.forwardCallerToken ?? false);
  readonly readOnlyTools = linkedSignal(() => this.server()?.readOnlyTools.join(', ') ?? '');
  readonly authHeader = signal('');
  readonly clearAuthHeader = signal(false);
  readonly error = signal('');
  readonly busy = signal(false);

  async save(): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    const form = {
      name: this.name(),
      url: this.url(),
      access: this.access(),
      forwardCallerToken: this.forwardCallerToken(),
      authHeader: this.authHeader(),
      clearAuthHeader: this.clearAuthHeader(),
      readOnlyTools: this.readOnlyTools(),
    };
    const existing = this.server();
    try {
      const saved = existing
        ? await this.agents.updateServer(this.agentId(), existing, form)
        : await this.agents.addServer(this.agentId(), form);
      // Sent once; nothing of it stays on the page.
      this.authHeader.set('');
      this.clearAuthHeader.set(false);
      this.saved.emit(saved);
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.busy.set(false);
    }
  }
}
