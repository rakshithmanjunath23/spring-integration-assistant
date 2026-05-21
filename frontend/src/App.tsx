import { useEffect } from 'react';
import { useUIStore } from './stores/uiStore';
import FileBrowser from './components/sidebar/FileBrowser';
import TopBar from './components/layout/TopBar';
import ToastContainer from './components/layout/ToastContainer';
import { ChatArea } from './components/chat';

const DESKTOP_BREAKPOINT = 1024;

function App() {
  const { theme, sidebarOpen, toggleSidebar, setSidebarOpen } = useUIStore();

  // Apply theme class to document root
  useEffect(() => {
    const root = document.documentElement;
    if (theme === 'dark') {
      root.classList.add('dark');
    } else {
      root.classList.remove('dark');
    }
  }, [theme]);

  // Responsive: collapse sidebar on mobile/tablet by default
  useEffect(() => {
    function handleResize() {
      if (window.innerWidth < DESKTOP_BREAKPOINT) {
        setSidebarOpen(false);
      } else {
        setSidebarOpen(true);
      }
    }

    // Set initial state
    handleResize();

    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, [setSidebarOpen]);

  return (
    <div className="h-screen flex bg-background text-foreground overflow-hidden">
      {/* Sidebar with CSS transition */}
      <div
        className={`transition-all duration-250 ease-in-out overflow-hidden shrink-0 ${
          sidebarOpen ? 'w-72' : 'w-0'
        }`}
      >
        <FileBrowser onClose={toggleSidebar} />
      </div>

      {/* Overlay for mobile when sidebar is open */}
      {sidebarOpen && (
        <div
          className="fixed inset-0 bg-black/30 z-40 lg:hidden"
          onClick={toggleSidebar}
          aria-hidden="true"
        />
      )}

      {/* Main Content */}
      <div className="flex-1 flex flex-col min-w-0">
        <TopBar />
        <ChatArea />
      </div>

      {/* Toast notifications */}
      <ToastContainer />
    </div>
  );
}

export default App;
