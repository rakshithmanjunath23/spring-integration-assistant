import { create } from 'zustand';
import type { FileStore, FileInfo } from '../types';
import { getFiles, getIndexStatus, triggerReindex } from '../services/api';

const MAX_SELECTED_FILES = 20;

export const useFileStore = create<FileStore>((set, get) => ({
  files: [],
  selectedFiles: [],
  searchQuery: '',
  indexStatus: {
    status: 'IDLE',
    totalFiles: 0,
    indexedFiles: 0,
    totalChunks: 0,
  },

  toggleFileSelection: (path: string) => {
    set((state) => {
      const isSelected = state.selectedFiles.includes(path);
      if (isSelected) {
        return { selectedFiles: state.selectedFiles.filter((f) => f !== path) };
      }
      if (state.selectedFiles.length >= MAX_SELECTED_FILES) {
        return state; // Do not exceed max selection
      }
      return { selectedFiles: [...state.selectedFiles, path] };
    });
  },

  clearSelection: () => {
    set({ selectedFiles: [] });
  },

  setSearchQuery: (query: string) => {
    set({ searchQuery: query });
  },

  triggerReindex: async (mode: 'full' | 'incremental') => {
    try {
      const status = await triggerReindex(mode);
      set({ indexStatus: status });
    } catch {
      set((state) => ({
        indexStatus: { ...state.indexStatus, status: 'ERROR', errorMessage: 'Failed to trigger reindex' },
      }));
    }
  },

  loadFiles: async () => {
    try {
      const response = await getFiles(0, 100);
      set({ files: response.content });
    } catch {
      // Keep existing files on error
    }
  },

  loadStatus: async () => {
    try {
      const indexStatus = await getIndexStatus();
      set({ indexStatus });
    } catch {
      // Keep existing status on error
    }
  },

  filteredFiles: (): FileInfo[] => {
    const { files, searchQuery } = get();
    if (!searchQuery.trim()) {
      return files;
    }
    const query = searchQuery.toLowerCase();
    return files.filter(
      (file) =>
        file.fileName.toLowerCase().includes(query) ||
        file.integrationName.toLowerCase().includes(query) ||
        file.fileType.toLowerCase().includes(query)
    );
  },
}));
