# Spring Integration AI Assistant

An intelligent ChatGPT-style AI assistant for analyzing Spring Integration projects using RAG (Retrieval-Augmented Generation) with Grok LLM.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Frontend (React + Vite)                    │
│  ┌──────────┐  ┌──────────────┐  ┌───────────────────────────┐ │
│  │ Sidebar  │  │  Chat Area   │  │    File Browser/Select     │ │
│  │ (Files)  │  │  (Streaming) │  │    (Multi-select)          │ │
│  └──────────┘  └──────────────┘  └───────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              │ SSE / REST
┌─────────────────────────────────────────────────────────────────┐
│                     Backend (Spring Boot 3.x)                    │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌──────────┐ │
│  │ Controller │  │  RAG Engine │  │  Indexer   │  │  Vector  │ │
│  │   Layer    │──│  (Retrieve  │──│  (Scan +   │──│  Store   │ │
│  │            │  │   + Augment)│  │   Chunk)   │  │  (H2)    │ │
│  └────────────┘  └────────────┘  └────────────┘  └──────────┘ │
│                         │                                        │
│                    ┌────────────┐                                │
│                    │  Grok API  │                                │
│                    │  (xAI LLM) │                                │
│                    └────────────┘                                │
└─────────────────────────────────────────────────────────────────┘
```

## Tech Stack

### Backend
- Java 21, Spring Boot 3.x, Spring WebFlux
- LangChain4j for RAG orchestration
- H2 (dev) / PostgreSQL (prod) for persistence
- In-memory vector store with cosine similarity

### Frontend
- React 18, TypeScript, Vite
- TailwindCSS, shadcn/ui components
- Server-Sent Events for streaming

### Infrastructure
- Docker + docker-compose
- Environment variable configuration

## Quick Start

### Prerequisites
- Java 21+
- Node.js 18+
- Maven 3.9+
- Docker (optional)

### Backend
```bash
cd backend
mvn spring-boot:run
```

### Frontend
```bash
cd frontend
npm install
npm run dev
```

### Docker
```bash
docker-compose up --build
```

## Configuration

Copy `.env.example` to `.env` and set your Grok API key:
```
GROK_API_KEY=your-key-here
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /api/files | List indexed files |
| POST | /api/files/reindex | Trigger re-indexing |
| GET | /api/files/content?path=... | Get file content |
| POST | /api/chat | Send chat message |
| GET | /api/chat/stream?sessionId=...&message=... | Stream response |
| GET | /api/chat/history?sessionId=... | Get chat history |
| DELETE | /api/chat/session/{id} | Delete session |

## Features

- 🔍 RAG-powered contextual answers with source citations
- 📁 File browser with multi-select for focused analysis
- 💬 Multi-turn conversational memory
- ⚡ Real-time streaming responses (SSE)
- 🏗️ Spring Integration architecture awareness
- 🌙 Dark/Light mode
- 📊 Integration flow analysis
