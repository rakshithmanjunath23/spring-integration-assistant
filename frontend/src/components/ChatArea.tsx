import { useState, useRef, useEffect } from 'react';
import type { ChatMessage } from '../types';
import MessageBubble from './MessageBubble';

interface ChatAreaProps {
  messages: ChatMessage[];
  isLoading: boolean;
  onSendMessage: (message: string) => void;
  selectedFiles: string[];
}

export default function ChatArea({ messages, isLoading, onSendMessage, selectedFiles }: ChatAreaProps) {
  const [input, setInput] = useState('');
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isLoading]);

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!input.trim() || isLoading) return;
    onSendMessage(input.trim());
    setInput('');
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
    }
  }

  function handleKeyDown(e: React.KeyboardEvent) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSubmit(e);
    }
  }

  function handleTextareaChange(e: React.ChangeEvent<HTMLTextAreaElement>) {
    setInput(e.target.value);
    // Auto-resize
    e.target.style.height = 'auto';
    e.target.style.height = Math.min(e.target.scrollHeight, 200) + 'px';
  }

  return (
    <div className="flex-1 flex flex-col min-h-0">
      {/* Messages */}
      <div className="flex-1 overflow-y-auto px-4 py-6">
        {messages.length === 0 ? (
          <div className="h-full flex flex-col items-center justify-center text-center">
            <div className="text-6xl mb-4">🏗️</div>
            <h2 className="text-2xl font-semibold mb-2 text-dark-800 dark:text-dark-200">
              Spring Integration Assistant
            </h2>
            <p className="text-dark-500 dark:text-dark-400 max-w-md">
              Ask me about your Spring Integration project architecture, message flows,
              configurations, and implementation details.
            </p>
            <div className="mt-6 grid grid-cols-1 sm:grid-cols-2 gap-2 max-w-lg">
              {[
                'Explain the holdingInquiry integration flow',
                'What channels are defined in ixconfigurations.xml?',
                'List all outbound integrations',
                'How does error handling work in this project?',
              ].map((suggestion, i) => (
                <button
                  key={i}
                  onClick={() => onSendMessage(suggestion)}
                  className="text-left text-sm px-4 py-3 rounded-xl border border-dark-200 dark:border-dark-700
                             hover:bg-dark-50 dark:hover:bg-dark-800 transition-colors text-dark-600 dark:text-dark-300"
                >
                  {suggestion}
                </button>
              ))}
            </div>
          </div>
        ) : (
          <div className="max-w-3xl mx-auto space-y-4">
            {messages.map(msg => (
              <MessageBubble key={msg.id} message={msg} />
            ))}
            {isLoading && (
              <div className="chat-bubble-assistant inline-block">
                <div className="flex items-center gap-1">
                  <div className="w-2 h-2 bg-dark-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
                  <div className="w-2 h-2 bg-dark-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
                  <div className="w-2 h-2 bg-dark-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
                </div>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>
        )}
      </div>

      {/* Input Area */}
      <div className="border-t border-dark-200 dark:border-dark-700 p-4 bg-white dark:bg-dark-900">
        {selectedFiles.length > 0 && (
          <div className="mb-2 flex flex-wrap gap-1">
            {selectedFiles.slice(0, 3).map(f => (
              <span key={f} className="text-[10px] bg-primary-50 dark:bg-primary-900/20 text-primary-700 dark:text-primary-300 px-2 py-0.5 rounded-full">
                {f.split('/').pop()}
              </span>
            ))}
            {selectedFiles.length > 3 && (
              <span className="text-[10px] text-dark-400">+{selectedFiles.length - 3} more</span>
            )}
          </div>
        )}
        <form onSubmit={handleSubmit} className="max-w-3xl mx-auto flex gap-2">
          <textarea
            ref={textareaRef}
            value={input}
            onChange={handleTextareaChange}
            onKeyDown={handleKeyDown}
            placeholder="Ask about your Spring Integration project..."
            rows={1}
            className="flex-1 resize-none px-4 py-3 rounded-xl border border-dark-200 dark:border-dark-700
                       bg-dark-50 dark:bg-dark-800 focus:outline-none focus:ring-2 focus:ring-primary-500
                       text-sm placeholder:text-dark-400"
          />
          <button
            type="submit"
            disabled={!input.trim() || isLoading}
            className="px-4 py-3 rounded-xl bg-primary-600 text-white font-medium text-sm
                       hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed
                       transition-colors shrink-0"
          >
            Send
          </button>
        </form>
      </div>
    </div>
  );
}
