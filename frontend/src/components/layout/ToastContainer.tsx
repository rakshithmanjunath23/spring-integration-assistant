import { CheckCircle2, Info, AlertTriangle, XCircle, X } from 'lucide-react';
import { useUIStore } from '../../stores/uiStore';
import type { Toast } from '../../types';

const TOAST_ICONS: Record<Toast['type'], typeof Info> = {
  info: Info,
  success: CheckCircle2,
  warning: AlertTriangle,
  error: XCircle,
};

const TOAST_STYLES: Record<Toast['type'], string> = {
  info: 'border-blue-200 dark:border-blue-800 bg-blue-50 dark:bg-blue-950/50 text-blue-900 dark:text-blue-100',
  success:
    'border-green-200 dark:border-green-800 bg-green-50 dark:bg-green-950/50 text-green-900 dark:text-green-100',
  warning:
    'border-yellow-200 dark:border-yellow-800 bg-yellow-50 dark:bg-yellow-950/50 text-yellow-900 dark:text-yellow-100',
  error:
    'border-red-200 dark:border-red-800 bg-red-50 dark:bg-red-950/50 text-red-900 dark:text-red-100',
};

function ToastItem({ toast }: { toast: Toast }) {
  const { dismissToast } = useUIStore();
  const Icon = TOAST_ICONS[toast.type];

  return (
    <div
      role="alert"
      className={`flex items-start gap-3 px-4 py-3 rounded-lg border shadow-lg
        animate-in slide-in-from-right-full duration-300 ${TOAST_STYLES[toast.type]}`}
    >
      <Icon className="w-4 h-4 mt-0.5 shrink-0" />
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium">{toast.title}</p>
        {toast.message && (
          <p className="text-xs mt-0.5 opacity-80">{toast.message}</p>
        )}
      </div>
      <button
        onClick={() => dismissToast(toast.id)}
        className="p-0.5 rounded hover:bg-black/10 dark:hover:bg-white/10 transition-colors shrink-0"
        aria-label="Dismiss notification"
      >
        <X className="w-3.5 h-3.5" />
      </button>
    </div>
  );
}

export default function ToastContainer() {
  const { toasts } = useUIStore();

  if (toasts.length === 0) return null;

  return (
    <div
      aria-live="polite"
      aria-label="Notifications"
      className="fixed top-4 right-4 z-50 flex flex-col gap-2 w-80 max-w-[calc(100vw-2rem)]"
    >
      {toasts.map((toast) => (
        <ToastItem key={toast.id} toast={toast} />
      ))}
    </div>
  );
}
