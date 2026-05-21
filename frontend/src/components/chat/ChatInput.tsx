import { useState, useRef, useCallback } from 'react';
import { Send, Loader2 } from 'lucide-react';

const MAX_CHARS = 4000;

interface ChatInputProps {
  onSendMessage: (message: string) => void;
  isStreaming: boolean;
  selectedFiles: string[];
}

export default function ChatInput({ onSendMessage, isStreaming, selectedFiles }: ChatInputProps) {
  const [input, setInput] = useState('');
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const charCount = input.length;
  const canSend = input.trim().length > 0 && !isStreaming && charCount <= MAX_CHARS;

  const handleSubmit = useCallback(() => {
    if (!canSend) return;
    onSendMessage(input.trim());
    setInput('');
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
    }
  }, [canSend, input, onSendMessage]);

  function handleKeyDown(e: React.KeyboardEvent) {
    if (e.key === 'Enter' && e.ctrlKey) {
      e.preventDefault();
      handleSubmit();
    }
  }

  function handleChange(e: React.ChangeEvent<HTMLTextAreaElement>) {
    const value = e.target.value;
    if (value.length <= MAX_CHARS) {
      setInput(value);
    }
    // Auto-resize
    const textarea = e.target;
    textarea.style.height = 'auto';
    textarea.style.height = Math.min(textarea.scrollHeight, 200) + 'px';
  }

  return (
    <div className="border-t border-border p-4 bg-background">
      {/* Selected files indicator */}
      {selectedFiles.length > 0 && (
        <div className="mb-2 flex flex-wrap gap-1 max-w-3xl mx-auto">
          {selectedFiles.slice(0, 3).map((f) => (
            <span
              key={f}
              className="text-[10px] bg-primary/10 text-primary px-2 py-0.5 rounded-full"
            >
              {f.split('/').pop()}
            </span>
          ))}
          {selectedFiles.length > 3 && (
            <span className="text-[10px] text-muted-foreground">
              +{selectedFiles.length - 3} more
            </span>
          )}
        </div>
      )}

      <div className="max-w-3xl mx-auto">
        <div className="relative flex items-end gap-2">
          <div className="flex-1 relative">
            <textarea
              ref={textareaRef}
              value={input}
              onChange={handleChange}
              onKeyDown={handleKeyDown}
              placeholder={
                isStreaming
                  ? 'Generating...'
                  : 'Ask about your Spring Integration project... (Ctrl+Enter to send)'
              }
              disabled={isStreaming}
              rows={1}
              className="w-full resize-none px-4 py-3 pr-16 rounded-xl border border-input
                         bg-background focus:outline-none focus:ring-2 focus:ring-ring
                         text-sm placeholder:text-muted-foreground
                         disabled:opacity-50 disabled:cursor-not-allowed"
              aria-label="Chat message input"
            />
            {/* Character count */}
            <span
              className={`absolute bottom-2 right-3 text-[10px] ${
                charCount > MAX_CHARS * 0.9
                  ? 'text-destructive'
                  : 'text-muted-foreground'
              }`}
            >
              {charCount}/{MAX_CHARS}
            </span>
          </div>

          {/* Send button */}
          <button
            onClick={handleSubmit}
            disabled={!canSend}
            className="shrink-0 p-3 rounded-xl bg-primary text-primary-foreground
                       hover:bg-primary/90 disabled:opacity-50 disabled:cursor-not-allowed
                       transition-colors"
            aria-label="Send message"
          >
            {isStreaming ? (
              <Loader2 className="w-5 h-5 animate-spin" />
            ) : (
              <Send className="w-5 h-5" />
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
