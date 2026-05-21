import { useRef, useEffect } from 'react';
import { useChatStore } from '../../stores/chatStore';
import { useFileStore } from '../../stores/fileStore';
import MessageBubble from './MessageBubble';
import StreamingMessage from './StreamingMessage';
import ChatInput from './ChatInput';

export default function ChatArea() {
  const messages = useChatStore((state) => state.messages);
  const isStreaming = useChatStore((state) => state.isStreaming);
  const sendMessage = useChatStore((state) => state.sendMessage);
  const selectedFiles = useFileStore((state) => state.selectedFiles);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isStreaming]);

  function handleSendMessage(message: string) {
    sendMessage(message, selectedFiles);
  }

  return (
    <div className="flex-1 flex flex-col min-h-0">
      {/* Messages */}
      <div className="flex-1 overflow-y-auto px-4 py-6">
        {messages.length === 0 && !isStreaming ? (
          <div className="h-full flex flex-col items-center justify-center text-center">
            <div className="text-6xl mb-4">🏗️</div>
            <h2 className="text-2xl font-semibold mb-2 text-foreground">
              Spring Integration Assistant
            </h2>
            <p className="text-muted-foreground max-w-md">
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
                  onClick={() => handleSendMessage(suggestion)}
                  className="text-left text-sm px-4 py-3 rounded-xl border border-border
                             hover:bg-accent transition-colors text-muted-foreground"
                >
                  {suggestion}
                </button>
              ))}
            </div>
          </div>
        ) : (
          <div className="max-w-3xl mx-auto space-y-4">
            {messages.map((msg, index) => (
              <MessageBubble
                key={msg.id}
                message={msg}
                isLast={index === messages.length - 1}
                onSuggestedQuestionClick={handleSendMessage}
              />
            ))}
            {isStreaming && <StreamingMessage />}
            <div ref={messagesEndRef} />
          </div>
        )}
      </div>

      {/* Input Area */}
      <ChatInput
        onSendMessage={handleSendMessage}
        isStreaming={isStreaming}
        selectedFiles={selectedFiles}
      />
    </div>
  );
}
