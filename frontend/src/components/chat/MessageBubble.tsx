import ReactMarkdown from 'react-markdown';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { oneDark } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { Copy, Check } from 'lucide-react';
import { useState } from 'react';
import type { ChatMessage } from '../../types';
import SourceCitations from './SourceCitations';
import SuggestedQuestions from './SuggestedQuestions';
import MermaidDiagram from './MermaidDiagram';

interface MessageBubbleProps {
  message: ChatMessage;
  isLast: boolean;
  onSuggestedQuestionClick: (question: string) => void;
}

function CodeBlock({ language, code }: { language: string; code: string }) {
  const [copied, setCopied] = useState(false);

  function handleCopy() {
    navigator.clipboard.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }

  return (
    <div className="relative group my-2">
      <div className="absolute top-2 right-2 z-10">
        <button
          onClick={handleCopy}
          className="flex items-center gap-1 px-2 py-1 text-xs rounded
                     bg-secondary text-secondary-foreground opacity-0 group-hover:opacity-100
                     transition-opacity"
          aria-label="Copy code"
        >
          {copied ? (
            <>
              <Check className="w-3 h-3" />
              Copied
            </>
          ) : (
            <>
              <Copy className="w-3 h-3" />
              Copy
            </>
          )}
        </button>
      </div>
      <SyntaxHighlighter
        style={oneDark}
        language={language}
        PreTag="div"
        customStyle={{ borderRadius: '0.5rem', fontSize: '0.8rem' }}
      >
        {code}
      </SyntaxHighlighter>
    </div>
  );
}

export default function MessageBubble({ message, isLast, onSuggestedQuestionClick }: MessageBubbleProps) {
  const isUser = message.role === 'user';
  const timestamp = new Date(message.timestamp).toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit',
  });

  return (
    <div className={`flex ${isUser ? 'justify-end' : 'justify-start'}`}>
      <div
        className={`max-w-[85%] rounded-2xl px-4 py-3 ${
          isUser
            ? 'bg-primary text-primary-foreground'
            : 'bg-secondary text-secondary-foreground'
        }`}
      >
        {isUser ? (
          <p className="text-sm whitespace-pre-wrap">{message.content}</p>
        ) : (
          <div className="prose prose-sm dark:prose-invert max-w-none text-sm">
            <ReactMarkdown
              components={{
                code({ className, children, ...props }) {
                  const match = /language-(\w+)/.exec(className || '');
                  const codeString = String(children).replace(/\n$/, '');

                  if (match) {
                    // Render mermaid diagrams inline
                    if (match[1] === 'mermaid') {
                      return <MermaidDiagram code={codeString} />;
                    }
                    return <CodeBlock language={match[1]} code={codeString} />;
                  }
                  return (
                    <code
                      className="bg-muted px-1.5 py-0.5 rounded text-xs font-mono"
                      {...props}
                    >
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

        {/* Timestamp */}
        <p
          className={`text-[10px] mt-2 ${
            isUser ? 'text-primary-foreground/70' : 'text-muted-foreground'
          }`}
        >
          {timestamp}
        </p>

        {/* Source Citations */}
        {!isUser && message.citations && message.citations.length > 0 && (
          <SourceCitations citations={message.citations} />
        )}

        {/* Suggested Questions - only on last assistant message */}
        {!isUser && isLast && message.suggestedQuestions && message.suggestedQuestions.length > 0 && (
          <SuggestedQuestions
            questions={message.suggestedQuestions}
            onQuestionClick={onSuggestedQuestionClick}
          />
        )}
      </div>
    </div>
  );
}
