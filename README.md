<div align="center">

# 🔍 GitInsight AI

### An AI-Powered Engineering Intelligence Platform for GitHub Repositories

Not just another GitHub stats tool — GitInsight AI understands your codebase, ranks contributors, tracks file ownership, detects risk, and answers natural-language questions about any repository.

**🚀 [Live Demo](https://git-insight-eosin.vercel.app/)**

</div>

---

## 📖 Overview

**GitInsight AI** takes any public GitHub repository URL and transforms raw commit history into deep engineering insight — contributor rankings, file ownership, risk hotspots, health scores, and AI-generated explanations of how the codebase actually works. Ask it questions like *"Where is JWT implemented?"* and get a direct answer, powered by repository-aware AI.

---

## ✨ Major Features

### 1. Repository Analysis
Paste a GitHub URL and GitInsight instantly analyzes the full repository.

```
https://github.com/user/repository
```

### 2. Repository Overview
- Repository name, owner, and description
- Stars, forks, and watchers
- Language breakdown
- License, last updated date, and size

### 3. 👥 Contributor Intelligence
Ranks contributors based on:
- Number of commits
- Files modified
- Insertions & deletions
- Overall activity
- Contribution percentage

### 4. 📊 Commit Analytics
- Recent commits feed
- Commit frequency trends
- Commit timeline visualization
- Commit message analysis
- Most active days

### 5. 🗂️ File Ownership *(Flagship Feature)*
- Which developer owns each file
- Number of edits per file
- Contribution percentage per file
- Last modifier tracking

### 6. ❤️ Repository Health
Calculates metrics including:
- Commit frequency
- Bus factor
- Active contributor count
- Overall repository activity
- Maintenance score

### 7. 🤖 AI Repository Summary
Powered by an LLM (Gemini / OpenRouter), GitInsight generates:
- What the project does
- Architecture summary
- Tech stack detection
- Folder-by-folder explanation
- Main entry points
- How the project works end-to-end

### 8. 💬 AI Question Answering
Ask natural-language questions such as:
- *"Explain authentication."*
- *"Where is JWT implemented?"*
- *"Which files contain REST APIs?"*
- *"Where is database configuration?"*
- *"Explain this repository to a beginner."*

### 9. ⚠️ Risk Analysis
Identifies:
- Frequently modified files
- Large, risky commits
- Hotspot files
- Single-owner files (knowledge silos)
- High-risk modules

### 10. 📈 Visual Dashboards
Interactive charts for:
- Commits over time
- Language distribution
- Contributor breakdown
- File ownership
- Repository activity

---

## 🧪 Planned AI Features

### AST (Abstract Syntax Tree) Analysis
- Parse Java source code
- Build code relationships
- Understand classes and their structure
- Map method-level dependencies

### 🕸️ Knowledge Graph
Visualizing architectural relationships, e.g.:

```
Controller
    │
    ▼
Service
    │
    ▼
Repository
    │
    ▼
Database
```

### Vector Database / RAG
Originally planned with **pgvector**; alternative embedding-storage approaches are also being explored due to extension limitations.

Purpose:
- Semantic code search
- Natural-language repository Q&A
- Context-aware AI explanations

### 💻 AI Repository Chat
A ChatGPT-style experience for any GitHub repo:

```
Explain Login API
Where is OAuth?
Show payment flow.
Find SQL Injection risks.
```

---

## 🛣️ Future Roadmap

| Feature | Description |
|---|---|
| **Code Review AI** | Detects bad practices, suggests improvements & security recommendations |
| **PR Review Assistant** | Automatically summarizes pull requests |
| **Repository Comparison** | Compares two repos by complexity, contributors, languages, and activity |
| **Team Dashboard** | Team productivity, ownership, bottlenecks, and velocity for managers |
| **Recruiter Dashboard** | Analyzes a GitHub profile for coding consistency, tech stack, and OSS contributions |

---

## 🏗️ System Architecture

```
React Frontend
      │
      ▼
Spring Boot API
      │
      ▼
GitHub API / JGit
      │
      ▼
Repository Analysis Engine
      │
      ▼
PostgreSQL
      │
      ▼
AI Layer (Gemini / OpenRouter)
      │
      ▼
Dashboard
```

---

## 🚀 Tech Stack

### Frontend
- React
- Vite
- TypeScript
- Tailwind CSS
- Chart.js / Recharts

### Backend
- Java 21
- Spring Boot 3
- Spring AI
- Spring Data JPA
- Maven
- REST APIs

### Database
- PostgreSQL
- pgvector
- Supabase

### AI & RAG
- Retrieval-Augmented Generation (RAG)
- Spring AI
- LLM Integration
- Embedding Models
- Vector Search
- Prompt Engineering
- AI Repository Summarization
- AI-powered Repository Q&A

### APIs & Repository Analysis
- GitHub REST API
- JGit
- Contributor Analytics
- Commit Analytics
- File Ownership Analysis

### Visualization
- Interactive Analytics Dashboard
- Repository Insights
- Contributor Statistics
- Commit Timeline
- Language Distribution

### Deployment
- **Frontend:** Vercel
- **Backend:** Render

---

## 🔮 Future Enhancements
- AI Code Review
- Repository Chat Assistant
- Semantic Code Search
- Commit Summarization
- Pull Request Summarization
- Architecture Explanation
- Repository Health Score
- Natural Language Repository Queries

---

## 🌟 Why This Project Stands Out

Compared to a typical GitHub analyzer, **GitInsight AI** aims to be an **intelligent engineering platform**, not just a statistics dashboard. The long-term vision includes:

- AI-powered repository understanding
- File ownership intelligence
- Engineering analytics
- Repository health scoring
- Code Q&A with RAG
- Risk and hotspot detection
- Architecture visualization
- Recruiter and engineering manager insights

This combination positions GitInsight AI closer to an **AI engineering assistant** than a simple GitHub analytics tool.

---

## 🌐 Live Demo


🚀 **GitInsight AI Live Demo:** [https://git-insight-eosin.vercel.app/](https://git-insight-eosin.vercel.app/)

---

<div align="center">

Made with ☕ and Java, built for showcasing Java Backend + GenAI engineering skills.

</div>
