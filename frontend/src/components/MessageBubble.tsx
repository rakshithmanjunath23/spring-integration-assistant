import ReactMarkdown from 'react-markdown';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { oneDark } from 'react-syntax-highlighter/dist/esm/styles/prism';
import type { ChatMessage } from '../types';

interface MessageBubbleProps {
  message: ChatMessage;
}

export default function MessageBubble({ message }: MessageBubbleProps) {
  const isUser = message.role === 'user';

  return (
    <div className={`flex ${isUser ? 'justify-end' : 'justify-start'}`}>
      <div className={isUser ? 'chat-bubble-user' : 'chat-bubble-assistant'}>
        {isUser ? (
          <p className="text-sm whitespace-pre-wrap">{message.content}</p>
        ) : (
          <div className="markdown-body text-sm">
            <ReactMarkdown
              components={{
                code({ className, children, ...props }) {
                  const match = /language-(\w+)/.exec(className || '');
                  const codeString = String(children).replace(/\n$/, '');
                  
                  if (match) {
                    return (
                      <div className="relative group">
                        <button
                          onClick={() => navigator.clipboard.writeText(codeString)}
                          className="absolute top-2 right-2 opacity-0 group-hover:opacity-100 
                                     px-2 py-1 text-xs bg-dark-700 text-dark-200 rounded transition-opacity"
                        >
                          Copy
                        </button>
                        <SyntaxHighlighter
                          style={oneDark}
                          language={match[1]}
                          PreTag="div"
                        >
                          {codeString}
                        </SyntaxHighlighter>
                      </div>
                    );
                  }
                  return (
                    <code className={className} {...props}>
                      {children}
                    </code>
                  );
                },
              }}
            >
              {message.content}
            </ReactMarkdown>
          </div>
        )}

        {/* Citations */}
        {message.citations && message.citations.length > 0 && (
          <div className="mt-3 pt-3 border-t border-dark-200 dark:border-dark-700">
            <p className="text-[10px] font-medium text-dark-500 dark:text-dark-400 mb-1">
              📎 Sources:
            </p>
            {message.citations.map((citation, i) => (
              <div key={i} className="text-[10px] text-dark-400 dark:text-dark-500 flex items-center gap-1">
                <span className="text-primary-500">•</span>
                <span className="truncate">{citation.filePath}</span>
                <span className="text-dark-300">({Math.round(citation.relevanceScore * 100)}%)</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
