import { useEffect, useRef, useState } from 'react';
import mermaid from 'mermaid';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { oneDark } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { AlertTriangle, ZoomIn, ZoomOut, RotateCcw } from 'lucide-react';

interface MermaidDiagramProps {
  code: string;
}

let mermaidInitialized = false;

function initMermaid() {
  if (!mermaidInitialized) {
    mermaid.initialize({
      startOnLoad: false,
      theme: 'default',
      securityLevel: 'strict',
      fontFamily: 'inherit',
    });
    mermaidInitialized = true;
  }
}

export default function MermaidDiagram({ code }: MermaidDiagramProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [svgContent, setSvgContent] = useState<string>('');
  const [error, setError] = useState<string | null>(null);
  const [scale, setScale] = useState(1);
  const [translate, setTranslate] = useState({ x: 0, y: 0 });
  const [isDragging, setIsDragging] = useState(false);
  const [dragStart, setDragStart] = useState({ x: 0, y: 0 });

  useEffect(() => {
    initMermaid();

    let cancelled = false;
    const id = `mermaid-${crypto.randomUUID().replace(/-/g, '').slice(0, 12)}`;

    async function renderDiagram() {
      try {
        const { svg } = await mermaid.render(id, code.trim());
        if (!cancelled) {
          setSvgContent(svg);
          setError(null);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Failed to parse Mermaid diagram');
          setSvgContent('');
        }
        // Clean up any leftover element from failed render
        const el = document.getElementById(id);
        if (el) el.remove();
      }
    }

    renderDiagram();
    return () => {
      cancelled = true;
    };
  }, [code]);

  function handleZoomIn() {
    setScale((s) => Math.min(s + 0.25, 3));
  }

  function handleZoomOut() {
    setScale((s) => Math.max(s - 0.25, 0.25));
  }

  function handleReset() {
    setScale(1);
    setTranslate({ x: 0, y: 0 });
  }

  function handleMouseDown(e: React.MouseEvent) {
    setIsDragging(true);
    setDragStart({ x: e.clientX - translate.x, y: e.clientY - translate.y });
  }

  function handleMouseMove(e: React.MouseEvent) {
    if (!isDragging) return;
    setTranslate({
      x: e.clientX - dragStart.x,
      y: e.clientY - dragStart.y,
    });
  }

  function handleMouseUp() {
    setIsDragging(false);
  }

  function handleWheel(e: React.WheelEvent) {
    e.preventDefault();
    const delta = e.deltaY > 0 ? -0.1 : 0.1;
    setScale((s) => Math.max(0.25, Math.min(3, s + delta)));
  }

  function handleTouchStart(e: React.TouchEvent) {
    if (e.touches.length === 1) {
      setIsDragging(true);
      setDragStart({
        x: e.touches[0].clientX - translate.x,
        y: e.touches[0].clientY - translate.y,
      });
    }
  }

  function handleTouchMove(e: React.TouchEvent) {
    if (!isDragging || e.touches.length !== 1) return;
    setTranslate({
      x: e.touches[0].clientX - dragStart.x,
      y: e.touches[0].clientY - dragStart.y,
    });
  }

  function handleTouchEnd() {
    setIsDragging(false);
  }

  if (error) {
    return (
      <div className="my-2 rounded-lg border border-destructive/50 overflow-hidden">
        <div className="flex items-center gap-2 px-3 py-2 bg-destructive/10 text-destructive text-xs">
          <AlertTriangle className="w-3.5 h-3.5" />
          <span>Mermaid parse error: {error}</span>
        </div>
        <SyntaxHighlighter
          language="mermaid"
          style={oneDark}
          customStyle={{ borderRadius: 0, margin: 0, fontSize: '0.8rem' }}
        >
          {code}
        </SyntaxHighlighter>
      </div>
    );
  }

  if (!svgContent) {
    return (
      <div className="my-2 flex items-center justify-center h-24 bg-muted rounded-lg">
        <span className="text-xs text-muted-foreground">Rendering diagram...</span>
      </div>
    );
  }

  return (
    <div className="my-2 rounded-lg border border-border overflow-hidden">
      {/* Zoom controls */}
      <div className="flex items-center gap-1 px-2 py-1 bg-muted/50 border-b border-border">
        <button
          onClick={handleZoomIn}
          className="p-1 rounded hover:bg-muted transition-colors"
          aria-label="Zoom in"
        >
          <ZoomIn className="w-3.5 h-3.5" />
        </button>
        <button
          onClick={handleZoomOut}
          className="p-1 rounded hover:bg-muted transition-colors"
          aria-label="Zoom out"
        >
          <ZoomOut className="w-3.5 h-3.5" />
        </button>
        <button
          onClick={handleReset}
          className="p-1 rounded hover:bg-muted transition-colors"
          aria-label="Reset zoom"
        >
          <RotateCcw className="w-3.5 h-3.5" />
        </button>
        <span className="text-[10px] text-muted-foreground ml-1">{Math.round(scale * 100)}%</span>
      </div>

      {/* Diagram viewport */}
      <div
        ref={containerRef}
        className="overflow-hidden bg-white dark:bg-card cursor-grab active:cursor-grabbing"
        style={{ maxHeight: '400px' }}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
        onWheel={handleWheel}
        onTouchStart={handleTouchStart}
        onTouchMove={handleTouchMove}
        onTouchEnd={handleTouchEnd}
      >
        <div
          style={{
            transform: `translate(${translate.x}px, ${translate.y}px) scale(${scale})`,
            transformOrigin: 'center center',
            transition: isDragging ? 'none' : 'transform 0.1s ease',
          }}
          dangerouslySetInnerHTML={{ __html: svgContent }}
        />
      </div>
    </div>
  );
}
