import type { ChatStreamEvent, SourceCitation } from '../types';

const API_BASE = '/api';

export interface StreamCallbacks {
  onToken: (token: string) => void;
  onCitations: (citations: SourceCitation[]) => void;
  onSuggestions: (questions: string[]) => void;
  onDone: (sessionId: string, messageId: string) => void;
  onError: (error: string) => void;
}

/**
 * Creates an SSE connection to the chat stream endpoint.
 * Returns a cleanup function that closes the connection.
 */
export function createChatStream(
  sessionId: string,
  message: string,
  selectedFiles: string[],
  callbacks: StreamCallbacks
): () => void {
  const params = new URLSearchParams({
    sessionId,
    message,
  });
  selectedFiles.forEach((file) => params.append('selectedFiles', file));

  const url = `${API_BASE}/chat/stream?${params.toString()}`;
  const eventSource = new EventSource(url);

  eventSource.addEventListener('token', (event: MessageEvent) => {
    try {
      const data: ChatStreamEvent = JSON.parse(event.data);
      if (data.content) {
        callbacks.onToken(data.content);
      }
    } catch {
      // If data is plain text (not JSON), treat it as a token
      callbacks.onToken(event.data);
    }
  });

  eventSource.addEventListener('citations', (event: MessageEvent) => {
    try {
      const data: ChatStreamEvent = JSON.parse(event.data);
      if (data.citations) {
        callbacks.onCitations(data.citations);
      }
    } catch {
      callbacks.onError('Failed to parse citations event');
    }
  });

  eventSource.addEventListener('suggestions', (event: MessageEvent) => {
    try {
      const data: ChatStreamEvent = JSON.parse(event.data);
      if (data.questions) {
        callbacks.onSuggestions(data.questions);
      }
    } catch {
      callbacks.onError('Failed to parse suggestions event');
    }
  });

  eventSource.addEventListener('done', (event: MessageEvent) => {
    try {
      const data: ChatStreamEvent = JSON.parse(event.data);
      callbacks.onDone(data.sessionId ?? sessionId, data.messageId ?? '');
    } catch {
      callbacks.onDone(sessionId, '');
    }
    eventSource.close();
  });

  eventSource.addEventListener('error', (event: MessageEvent) => {
    if (event instanceof MessageEvent && event.data) {
      try {
        const data: ChatStreamEvent = JSON.parse(event.data);
        callbacks.onError(data.content ?? 'Stream error occurred');
      } catch {
        callbacks.onError(event.data);
      }
    }
  });

  // Handle connection-level errors (network issues, server disconnect)
  eventSource.onerror = () => {
    if (eventSource.readyState === EventSource.CLOSED) {
      return; // Normal close, already handled by 'done' event
    }
    eventSource.close();
    callbacks.onError('Stream connection lost. Please try again.');
  };

  return () => {
    eventSource.close();
  };
}
