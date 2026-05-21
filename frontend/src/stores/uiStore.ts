import { create } from 'zustand';
import type { UIStore, Toast } from '../types';

const THEME_STORAGE_KEY = 'spring-integration-assistant-theme';
const MAX_VISIBLE_TOASTS = 3;
const AUTO_DISMISS_MS = 5000;

function getInitialTheme(): 'light' | 'dark' {
  // Check localStorage first
  const stored = localStorage.getItem(THEME_STORAGE_KEY);
  if (stored === 'light' || stored === 'dark') {
    return stored;
  }
  // Fall back to OS preference
  if (window.matchMedia('(prefers-color-scheme: dark)').matches) {
    return 'dark';
  }
  return 'light';
}

export const useUIStore = create<UIStore>((set, get) => ({
  theme: getInitialTheme(),
  sidebarOpen: true,
  toasts: [],

  toggleTheme: () => {
    set((state) => {
      const newTheme = state.theme === 'light' ? 'dark' : 'light';
      localStorage.setItem(THEME_STORAGE_KEY, newTheme);
      return { theme: newTheme };
    });
  },

  toggleSidebar: () => {
    set((state) => ({ sidebarOpen: !state.sidebarOpen }));
  },

  setSidebarOpen: (open: boolean) => {
    set({ sidebarOpen: open });
  },

  addToast: (toast: Omit<Toast, 'id'>) => {
    const id = crypto.randomUUID();
    const newToast: Toast = { ...toast, id };

    set((state) => {
      // Keep only the most recent toasts up to max visible
      const toasts = [newToast, ...state.toasts].slice(0, MAX_VISIBLE_TOASTS);
      return { toasts };
    });

    // Auto-dismiss non-persistent toasts after 5 seconds
    if (!toast.persistent) {
      setTimeout(() => {
        get().dismissToast(id);
      }, AUTO_DISMISS_MS);
    }
  },

  dismissToast: (id: string) => {
    set((state) => ({
      toasts: state.toasts.filter((t) => t.id !== id),
    }));
  },
}));
