import type {
  ChatMessage,
  ChatRequest,
  ChatSession,
  FileInfo,
  IndexStatus,
  ErrorResponse,
} from '../types';

const API_BASE = '/api';

/**
 * Generic fetch wrapper with error handling and JSON parsing.
 */
async function fetchJson<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${url}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });

  if (!response.ok) {
    let errorMessage = `API error: ${response.status} ${response.statusText}`;
    try {
      const errorBody: { error?: ErrorResponse } = await response.json();
      if (errorBody.error?.message) {
        errorMessage = errorBody.error.message;
      }
    } catch {
      // Use default error message if body isn't parseable
    }
    throw new Error(errorMessage);
  }

  // Handle 204 No Content
  if (response.status === 204) {
    return undefined as unknown as T;
  }

  return response.json();
}

// ─── File APIs ───────────────────────────────────────────────────────────────

export interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export async function getFiles(page = 0, size = 20): Promise<PaginatedResponse<FileInfo>> {
  return fetchJson<PaginatedResponse<FileInfo>>(
    `/files?page=${page}&size=${size}`
  );
}

export async function searchFiles(query: string): Promise<FileInfo[]> {
  return fetchJson<FileInfo[]>(
    `/files?search=${encodeURIComponent(query)}`
  );
}

export async function getFileContent(path: string): Promise<string> {
  const response = await fetch(
    `${API_BASE}/files/content?path=${encodeURIComponent(path)}`
  );
  if (!response.ok) {
    throw new Error(`Failed to fetch file content: ${response.status}`);
  }
  return response.text();
}

export async function triggerReindex(mode: 'full' | 'incremental' = 'incremental'): Promise<IndexStatus> {
  return fetchJson<IndexStatus>('/files/reindex', {
    method: 'POST',
    body: JSON.stringify({ mode }),
  });
}

export async function getIndexStatus(): Promise<IndexStatus> {
  return fetchJson<IndexStatus>('/files/status');
}

// ─── Chat APIs ───────────────────────────────────────────────────────────────

export interface ChatResponse {
  id: string;
  sessionId: string;
  role: 'assistant';
  message: string;
  citations?: ChatMessage['citations'];
  suggestedQuestions?: string[];
  timestamp: string;
}

export async function sendChatMessage(request: ChatRequest): Promise<ChatResponse> {
  return fetchJson<ChatResponse>('/chat', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export async function getChatHistory(sessionId: string): Promise<ChatMessage[]> {
  const result = await fetchJson<{ sessionId: string; messages: ChatMessage[] }>(
    `/chat/history?sessionId=${encodeURIComponent(sessionId)}`
  );
  return result.messages;
}

export async function deleteChatSession(sessionId: string): Promise<void> {
  await fetchJson<void>(`/chat/session/${encodeURIComponent(sessionId)}`, {
    method: 'DELETE',
  });
}

export async function getChatSessions(): Promise<ChatSession[]> {
  return fetchJson<ChatSession[]>('/chat/sessions');
}
