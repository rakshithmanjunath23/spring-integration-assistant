import { Download } from 'lucide-react';
import { useChatStore } from '../../stores/chatStore';
import type { ChatMessage } from '../../types';

function formatCitations(message: ChatMessage): string {
  if (!message.citations || message.citations.length === 0) return '';

  const lines = message.citations.map(
    (c) =>
      `- [${c.integrationName}] ${c.filePath}${c.lineRange ? ` (lines ${c.lineRange})` : ''} — relevance: ${(c.relevanceScore * 100).toFixed(0)}%`
  );
  return '\n\n**Citations:**\n' + lines.join('\n');
}

function generateMarkdown(messages: ChatMessage[]): string {
  if (messages.length === 0) return '';

  // Header
  const sessionDate = messages[0]?.timestamp
    ? new Date(messages[0].timestamp).toISOString()
    : new Date().toISOString();

  // Collect unique integration names from citations
  const integrationNames = new Set<string>();
  for (const msg of messages) {
    if (msg.citations) {
      for (const c of msg.citations) {
        integrationNames.add(c.integrationName);
      }
    }
  }

  const header = [
    '# Chat Export',
    '',
    `**Date:** ${sessionDate}`,
    integrationNames.size > 0
      ? `**Integrations:** ${Array.from(integrationNames).sort().join(', ')}`
      : '',
    `**Messages:** ${messages.length}`,
    '',
    '---',
    '',
  ]
    .filter(Boolean)
    .join('\n');

  // Messages
  const body = messages
    .map((msg) => {
      const role = msg.role === 'user' ? 'User' : 'Assistant';
      const content = msg.content;
      const citations = msg.role === 'assistant' ? formatCitations(msg) : '';
      return `### ${role}\n\n${content}${citations}`;
    })
    .join('\n\n---\n\n');

  return header + body + '\n';
}

export default function ExportButton() {
  const { messages } = useChatStore();
  const disabled = messages.length === 0;

  function handleExport() {
    if (disabled) return;

    const markdown = generateMarkdown(messages);
    const blob = new Blob([markdown], { type: 'text/markdown;charset=utf-8' });
    const url = URL.createObjectURL(blob);

    const a = document.createElement('a');
    a.href = url;
    a.download = `chat-export-${new Date().toISOString().slice(0, 10)}.md`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }

  return (
    <button
      onClick={handleExport}
      disabled={disabled}
      className="p-2 rounded-lg hover:bg-muted transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
      aria-label="Export chat as Markdown"
      title={disabled ? 'No messages to export' : 'Export chat as Markdown'}
    >
      <Download className="w-4 h-4" />
    </button>
  );
}
