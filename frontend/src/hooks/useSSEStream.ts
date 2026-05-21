import { useCallback, useRef, useState } from 'react';
import { createChatStream, type StreamCallbacks } from '../services/sse';
import type { SourceCitation } from '../types';

export interface UseSSEStreamOptions {
  onToken?: (token: string) => void;
  onCitations?: (citations: SourceCitation[]) => void;
  onSuggestions?: (questions: string[]) => void;
  onDone?: (sessionId: string, messageId: string) => void;
  onError?: (error: string) => void;
}

export interface UseSSEStreamReturn {
  startStream: (sessionId: string, message: string, selectedFiles: string[]) => void;
  stopStream: () => void;
  isStreaming: boolean;
}

/**
 * React hook for managing SSE chat stream connections.
 * Handles lifecycle, cleanup, and state tracking.
 */
export function useSSEStream(options: UseSSEStreamOptions = {}): UseSSEStreamReturn {
  const [isStreaming, setIsStreaming] = useState(false);
  const cleanupRef = useRef<(() => void) | null>(null);

  const stopStream = useCallback(() => {
    if (cleanupRef.current) {
      cleanupRef.current();
      cleanupRef.current = null;
    }
    setIsStreaming(false);
  }, []);

  const startStream = useCallback(
    (sessionId: string, message: string, selectedFiles: string[]) => {
      // Stop any existing stream before starting a new one
      stopStream();

      setIsStreaming(true);

      const callbacks: StreamCallbacks = {
        onToken: (token) => {
          options.onToken?.(token);
        },
        onCitations: (citations) => {
          options.onCitations?.(citations);
        },
        onSuggestions: (questions) => {
          options.onSuggestions?.(questions);
        },
        onDone: (sid, messageId) => {
          setIsStreaming(false);
          cleanupRef.current = null;
          options.onDone?.(sid, messageId);
        },
        onError: (error) => {
          setIsStreaming(false);
          cleanupRef.current = null;
          options.onError?.(error);
        },
      };

      cleanupRef.current = createChatStream(sessionId, message, selectedFiles, callbacks);
    },
    [stopStream, options]
  );

  return { startStream, stopStream, isStreaming };
}
