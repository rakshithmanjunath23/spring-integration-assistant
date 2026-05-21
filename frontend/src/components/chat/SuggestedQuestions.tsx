import { MessageCircleQuestion } from 'lucide-react';

interface SuggestedQuestionsProps {
  questions: string[];
  onQuestionClick: (question: string) => void;
}

export default function SuggestedQuestions({ questions, onQuestionClick }: SuggestedQuestionsProps) {
  if (questions.length === 0) return null;

  return (
    <div className="mt-3 pt-3 border-t border-border/50">
      <p className="text-[10px] font-medium text-muted-foreground mb-1.5 flex items-center gap-1">
        <MessageCircleQuestion className="w-3 h-3" />
        Follow-up questions
      </p>
      <div className="flex flex-wrap gap-1.5">
        {questions.map((question, i) => (
          <button
            key={i}
            onClick={() => onQuestionClick(question)}
            className="text-[11px] px-2.5 py-1 rounded-full border border-border
                       hover:bg-accent hover:text-accent-foreground
                       transition-colors text-muted-foreground"
          >
            {question}
          </button>
        ))}
      </div>
    </div>
  );
}
