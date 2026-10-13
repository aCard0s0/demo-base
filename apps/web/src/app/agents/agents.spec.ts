import { McpServer, ServerForm, serverBody } from './agents';

describe('serverBody', () => {
  const form: ServerForm = {
    name: ' github ',
    url: ' https://mcp.example.com/mcp ',
    access: 'READ',
    forwardCallerToken: true,
    authHeader: '',
    clearAuthHeader: false,
    readOnlyTools: 'list_issues, get_issue  search',
  };
  const saved = (over: Partial<McpServer>): McpServer => ({
    id: 1, name: 'github', url: 'https://mcp.example.com/mcp', access: 'READ', forwardCallerToken: false,
    hasAuthHeader: true, trusted: false, readOnlyTools: [], ...over,
  });

  it('never sends a header nobody typed, and clears one only when asked', () => {
    expect(serverBody(form, saved({}))).toEqual({
      name: 'github', url: 'https://mcp.example.com/mcp', access: 'READ', readOnlyTools: ['list_issues', 'get_issue', 'search'],
    });
    expect(serverBody({ ...form, authHeader: ' Bearer s3cret ' })['authHeader']).toBe('Bearer s3cret');
    expect(serverBody({ ...form, authHeader: 'Bearer typed', clearAuthHeader: true }, saved({}))['authHeader']).toBe('');
  });

  it('asks to forward the caller token only for a saved row agent-service trusts, or to turn it off', () => {
    expect('forwardCallerToken' in serverBody(form)).toBe(false);
    expect('forwardCallerToken' in serverBody(form, saved({}))).toBe(false);
    expect(serverBody(form, saved({ trusted: true }))['forwardCallerToken']).toBe(true);
    expect(serverBody({ ...form, forwardCallerToken: false }, saved({ forwardCallerToken: true }))['forwardCallerToken']).toBe(false);
  });
});
