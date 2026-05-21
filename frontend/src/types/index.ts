// Chat types
export interface ChatMessage {
  id: string;
  sessionId: string;
  role: 'user' | 'assistant';
  content: string;
  citations?: SourceCitation[];
  suggestedQuestions?: string[];
  timestamp: string;
  isStreaming?: boolean;
}

export interface SourceCitation {
  filePath: string;
  integrationName: string;
  relevanceScore: number;
  lineRange?: string;
}

export interface ChatSession {
  id: string;
  createdAt: string;
  lastMessageAt: string;
  messageCount: number;
}

// File types
export interface FileInfo {
  id: number;
  fileName: string;
  absolutePath: string;
  relativePath: string;
  integrationName: string;
  boundType: 'inbound' | 'outbound' | 'datamart' | 'common';
  fileType: string;
  fileSize: number;
  contentHash: string;
  indexed: boolean;
  chunkCount: number;
  concepts: string[];
}

export interface IndexStatus {
  jobId?: string;
  status: 'IDLE' | 'RUNNING' | 'COMPLETE' | 'ERROR';
  totalFiles: number;
  indexedFiles: number;
  totalChunks: number;
  errorMessage?: string;
  startedAt?: string;
  completedAt?: string;
}

// SSE Stream types
export interface ChatStreamEvent {
  type: 'TOKEN' | 'CITATIONS' | 'SUGGESTIONS' | 'DONE' | 'ERROR';
  content?: string;
  citations?: SourceCitation[];
  questions?: string[];
  sessionId?: string;
  messageId?: string;
}

// API types
export interface ChatRequest {
  message: string;
  sessionId?: string;
  selectedFiles?: string[];
}

export interface ErrorResponse {
  code: string;
  message: string;
  field?: string;
  timestamp: string;
  correlationId: string;
}

// Store types
export interface Toast {
  id: string;
  type: 'info' | 'success' | 'error' | 'warning';
  title: string;
  message?: string;
  persistent?: boolean;
}

// Zustand store interfaces
export interface ChatStore {
  sessions: ChatSession[];
  activeSessionId: string | null;
  messages: ChatMessage[];
  isStreaming: boolean;
  streamingContent: string;
  sendMessage: (message: string, selectedFiles: string[]) => Promise<void>;
  createSession: () => string;
  deleteSession: (id: string) => Promise<void>;
  loadHistory: (sessionId: string) => Promise<void>;
  loadSessions: () => Promise<void>;
  setActiveSession: (id: string) => void;
  appendStreamToken: (token: string) => void;
  finalizeStream: (citations?: SourceCitation[], suggestions?: string[]) => void;
}

export interface FileStore {
  files: FileInfo[];
  selectedFiles: string[];
  searchQuery: string;
  indexStatus: IndexStatus;
  toggleFileSelection: (path: string) => void;
  clearSelection: () => void;
  setSearchQuery: (query: string) => void;
  triggerReindex: (mode: 'full' | 'incremental') => Promise<void>;
  loadFiles: () => Promise<void>;
  loadStatus: () => Promise<void>;
  filteredFiles: () => FileInfo[];
}

export interface UIStore {
  theme: 'light' | 'dark';
  sidebarOpen: boolean;
  toasts: Toast[];
  toggleTheme: () => void;
  toggleSidebar: () => void;
  setSidebarOpen: (open: boolean) => void;
  addToast: (toast: Omit<Toast, 'id'>) => void;
  dismissToast: (id: string) => void;
}
