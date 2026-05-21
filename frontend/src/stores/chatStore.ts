import { create } from 'zustand';
import type { ChatStore, ChatMessage, ChatSession, SourceCitation, ChatStreamEvent } from '../types';
import { getChatHistory, getChatSessions, deleteChatSession } from '../services/api';

export const useChatStore = create<ChatStore>((set, get) => ({
  sessions: [],
  activeSessionId: null,
  messages: [],
  isStreaming: false,
  streamingContent: '',

  createSession: () => {
    const id = crypto.randomUUID();
    const now = new Date().toISOString();
    const session: ChatSession = {
      id,
      createdAt: now,
      lastMessageAt: now,
      messageCount: 0,
    };
    set((state) => ({
      sessions: [session, ...state.sessions],
      activeSessionId: id,
      messages: [],
      streamingContent: '',
    }));
    return id;
  },

  deleteSession: async (id: string) => {
    await deleteChatSession(id);
    set((state) => {
      const sessions = state.sessions.filter((s) => s.id !== id);
      const isActive = state.activeSessionId === id;
      return {
        sessions,
        activeSessionId: isActive ? (sessions[0]?.id ?? null) : state.activeSessionId,
        messages: isActive ? [] : state.messages,
        streamingContent: isActive ? '' : state.streamingContent,
      };
    });
  },

  loadSessions: async () => {
    const sessions = await getChatSessions();
    set({ sessions });
  },

  loadHistory: async (sessionId: string) => {
    const messages = await getChatHistory(sessionId);
    set({ messages, activeSessionId: sessionId });
  },

  setActiveSession: (id: string) => {
    set({ activeSessionId: id, messages: [], streamingContent: '' });
  },

  appendStreamToken: (token: string) => {
    set((state) => ({
      streamingContent: state.streamingContent + token,
    }));
  },

  finalizeStream: (citations?: SourceCitation[], suggestions?: string[]) => {
    set((state) => {
      const assistantMessage: ChatMessage = {
        id: crypto.randomUUID(),
        sessionId: state.activeSessionId ?? '',
        role: 'assistant',
        content: state.streamingContent,
        citations,
        suggestedQuestions: suggestions,
        timestamp: new Date().toISOString(),
        isStreaming: false,
      };
      return {
        messages: [...state.messages, assistantMessage],
        isStreaming: false,
        streamingContent: '',
      };
    });
  },

  sendMessage: async (message: string, selectedFiles: string[]) => {
    const state = get();
    let sessionId = state.activeSessionId;

    if (!sessionId) {
      sessionId = get().createSession();
    }

    // Add user message to the list
    const userMessage: ChatMessage = {
      id: crypto.randomUUID(),
      sessionId,
      role: 'user',
      content: message,
      timestamp: new Date().toISOString(),
    };

    set((state) => ({
      messages: [...state.messages, userMessage],
      isStreaming: true,
      streamingContent: '',
    }));

    // Open SSE stream to /api/chat/stream
    const params = new URLSearchParams({ sessionId, message });
    selectedFiles.forEach((f) => params.append('selectedFiles', f));

    const eventSource = new EventSource(`/api/chat/stream?${params.toString()}`);
    let citations: SourceCitation[] | undefined;
    let suggestions: string[] | undefined;

    eventSource.addEventListener('token', (event: MessageEvent) => {
      try {
        const data: ChatStreamEvent = JSON.parse(event.data);
        if (data.content) {
          get().appendStreamToken(data.content);
        }
      } catch {
        // Plain text token
        get().appendStreamToken(event.data);
      }
    });

    eventSource.addEventListener('citations', (event: MessageEvent) => {
      try {
        const data: ChatStreamEvent = JSON.parse(event.data);
        citations = data.citations;
      } catch {
        // ignore parse errors
      }
    });

    eventSource.addEventListener('suggestions', (event: MessageEvent) => {
      try {
        const data: ChatStreamEvent = JSON.parse(event.data);
        suggestions = data.questions;
      } catch {
        // ignore parse errors
      }
    });

    eventSource.addEventListener('done', () => {
      eventSource.close();
      get().finalizeStream(citations, suggestions);
    });

    eventSource.addEventListener('error', () => {
      eventSource.close();
      get().finalizeStream(citations, suggestions);
    });

    // Also handle generic onmessage for backward compatibility
    eventSource.onmessage = (event: MessageEvent) => {
      if (event.data === '[DONE]') {
        eventSource.close();
        get().finalizeStream(citations, suggestions);
      } else {
        try {
          const data: ChatStreamEvent = JSON.parse(event.data);
          if (data.type === 'TOKEN' && data.content) {
            get().appendStreamToken(data.content);
          } else if (data.type === 'CITATIONS') {
            citations = data.citations;
          } else if (data.type === 'SUGGESTIONS') {
            suggestions = data.questions;
          } else if (data.type === 'DONE') {
            eventSource.close();
            get().finalizeStream(citations, suggestions);
          }
        } catch {
          // Plain text token fallback
          get().appendStreamToken(event.data);
        }
      }
    };

    eventSource.onerror = () => {
      eventSource.close();
      get().finalizeStream(citations, suggestions);
    };
  },
}));
