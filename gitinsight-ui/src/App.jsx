import { useState, useRef, useEffect } from 'react';
import ReactMarkdown from 'react-markdown';
import mermaid from 'mermaid';

mermaid.initialize({
  startOnLoad: false,
  theme: 'neutral',
  fontFamily: "'Outfit', sans-serif",
  themeVariables: {
    primaryColor: '#6d5dfc',
    primaryTextColor: '#1e1f2e',
    primaryBorderColor: '#d6d3f8',
    lineColor: '#9b99c4',
    sectionBkgColor: '#f3f2ff',
    altSectionBkgColor: '#ede9ff',
    gridColor: '#e8e5ff',
    secondaryColor: '#f3f2ff',
    tertiaryColor: '#ede9ff',
  },
});

const API = 'http://localhost:8080/api/repositories';

/* ─── Markdown sanitizer ─────────────────────────────────────── */
function cleanMd(text = '') {
  return text
    .replace(/\*{3,}([^*]+)\*{3,}/g, '**$1**')
    .replace(/\*{4,}/g, '')
    .replace(/^\* /gm, '- ')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

/* ─── Design tokens — soft light theme ──────────────────────── */
const C = {
  bg:        '#f4f3ff',
  surface:   '#ffffff',
  raised:    '#f9f8ff',
  border:    '#e4e1fa',
  borderHi:  '#c9c4f5',
  violet:    '#6d5dfc',
  violetLo:  'rgba(109,93,252,0.08)',
  violetMd:  'rgba(109,93,252,0.20)',
  emerald:   '#0ea877',
  emeraldLo: 'rgba(14,168,119,0.09)',
  amber:     '#d97706',
  amberLo:   'rgba(217,119,6,0.09)',
  red:       '#e54e4e',
  text:      '#1a1b2e',
  textMd:    '#4b4d6b',
  textLo:    '#9b99c4',
  textLoLo:  '#cbc8ef',
};

const RANK_COLORS = [
  { bg:'rgba(217,119,6,0.10)',  border:'rgba(217,119,6,0.30)',  text:'#b45309' },
  { bg:'rgba(100,116,139,0.10)', border:'rgba(100,116,139,0.28)', text:'#475569' },
  { bg:'rgba(161,100,70,0.10)', border:'rgba(161,100,70,0.28)', text:'#92400e' },
];

/* ─── Global styles ──────────────────────────────────────────── */
const STYLES = `
  @import url('https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700;800&family=Fira+Code:wght@400;500&display=swap');
  *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
  :root { color-scheme: light; }
  body { background: ${C.bg}; }

  @keyframes fadeUp  { from{opacity:0;transform:translateY(14px)} to{opacity:1;transform:translateY(0)} }
  @keyframes fadeIn  { from{opacity:0} to{opacity:1} }
  @keyframes shimmer { from{background-position:-600px 0} to{background-position:600px 0} }
  @keyframes spin    { to{transform:rotate(360deg)} }
  @keyframes floatA  { 0%,100%{transform:translate(0,0) scale(1)} 50%{transform:translate(10px,-8px) scale(1.04)} }
  @keyframes floatB  { 0%,100%{transform:translate(0,0) scale(1)} 50%{transform:translate(-8px,10px) scale(1.03)} }
  @keyframes barGrow { from{width:0} to{width:var(--w)} }

  .fu { animation: fadeUp .4s cubic-bezier(.22,1,.36,1) both; }
  .fi { animation: fadeIn .3s ease both; }

  .skeleton {
    background: linear-gradient(90deg, ${C.border} 25%, #ebe8ff 50%, ${C.border} 75%);
    background-size: 600px 100%;
    animation: shimmer 1.6s infinite;
    border-radius: 14px;
  }
  .spin {
    width:15px; height:15px; border-radius:50%;
    border:2px solid ${C.violetMd}; border-top-color:${C.violet};
    animation:spin .65s linear infinite; display:inline-block; flex-shrink:0;
  }

  .chat-scroll::-webkit-scrollbar { width:3px }
  .chat-scroll::-webkit-scrollbar-track { background:transparent }
  .chat-scroll::-webkit-scrollbar-thumb { background:${C.border}; border-radius:3px }

  /* ── Markdown inside AI sections ── */
  .md h1,.md h2,.md h3 { color:${C.text}; font-family:'Outfit',sans-serif; font-weight:700; margin:1.2em 0 .4em }
  .md h1{font-size:17px} .md h2{font-size:15px} .md h3{font-size:14px; color:${C.violet};}
  .md p { color:${C.textMd}; font-size:13.5px; line-height:1.9; margin:0 0 .85em }
  .md code { background:${C.violetLo}; color:${C.violet}; padding:2px 6px; border-radius:5px; font-family:'Fira Code',monospace; font-size:11.5px; border:1px solid ${C.violetMd}; }
  .md pre { background:${C.raised}; padding:.85rem 1rem; border-radius:10px; overflow-x:auto; border:1px solid ${C.border}; margin:0 0 1em }
  .md pre code { background:none; padding:0; color:${C.textMd}; border:none; }
  .md ul,.md ol { padding-left:1.4rem; color:${C.textMd}; font-size:13.5px; margin:0 0 .85em }
  .md li { margin-bottom:6px; line-height:1.7; }
  .md strong { color:${C.violet}; font-weight:700; }
  .md em { color:${C.textMd}; font-style:italic }
  .md blockquote { border-left:3px solid ${C.violet}; padding:.4rem 1rem; color:${C.textLo}; margin:0 0 1em; background:${C.violetLo}; border-radius:0 8px 8px 0; }
  .md ul ul,.md ul ol,.md ol ul,.md ol ol { margin:.3em 0 .3em; padding-left:1.2rem }
  .md li>strong { color:${C.violet}; }

  input::placeholder { color:${C.textLoLo} }
  input { caret-color:${C.violet} }

  .panel {
    background:${C.surface};
    border:1px solid ${C.border};
    border-radius:18px;
    box-shadow: 0 1px 4px rgba(109,93,252,0.05), 0 4px 20px rgba(109,93,252,0.04);
  }
  .panel:hover { border-color:${C.borderHi}; box-shadow:0 2px 8px rgba(109,93,252,0.09), 0 6px 28px rgba(109,93,252,0.06); transition:all .2s }

  .contributor-row:hover { background:${C.raised}!important }

  .mermaid-wrap svg { max-width:100%; height:auto; }
  .mermaid-wrap .node rect { fill:${C.raised}!important; stroke:${C.borderHi}!important; }
`;

/* ─── Commit heatmap ──────────────────────────────────────────── */
function Heatmap({ dailyCounts }) {
  const data = Object.entries(dailyCounts || {}).slice(-42);
  const max  = Math.max(...data.map(([, v]) => v), 1);
  const color = (n) => !n ? C.border : `rgba(109,93,252,${Math.max(0.12, n / max * 0.85)})`;
  const glow  = (n) => n > max * 0.65 ? `0 0 5px rgba(109,93,252,0.30)` : 'none';
  return (
    <div>
      <div style={{ display:'flex', flexWrap:'wrap', gap:3 }}>
        {data.length ? data.map(([date, count]) => (
          <div key={date} title={`${date}: ${count} commit${count !== 1 ? 's' : ''}`}
            style={{ width:13, height:13, borderRadius:3, cursor:'default',
              transition:'transform .12s, box-shadow .12s',
              background:color(count),
              border:`1px solid rgba(109,93,252,${!count ? 0.1 : Math.min(count/max+0.1,0.55)})`,
              boxShadow:glow(count),
            }}
            onMouseEnter={e => { e.target.style.transform='scale(1.45)'; e.target.style.zIndex=5; }}
            onMouseLeave={e => { e.target.style.transform='scale(1)'; e.target.style.zIndex=''; }}
          />
        )) : <p style={{ fontSize:12, color:C.textLo, fontStyle:'italic' }}>No activity data.</p>}
      </div>
      <div style={{ display:'flex', alignItems:'center', gap:5, marginTop:9 }}>
        <span style={{ fontSize:10.5, color:C.textLo }}>less</span>
        {[0.07,0.18,0.38,0.62,0.85].map((o, i) => (
          <div key={i} style={{ width:11, height:11, borderRadius:2, background:`rgba(109,93,252,${o})` }} />
        ))}
        <span style={{ fontSize:10.5, color:C.textLo }}>more</span>
      </div>
    </div>
  );
}

/* ─── Contributors leaderboard ───────────────────────────────── */
function Contributors({ list }) {
  if (!list || !list.length) return null;
  const maxC = list[0]?.commitCount || 1;
  return (
    <div style={{ display:'flex', flexDirection:'column', gap:6 }}>
      {list.map((c, i) => {
        const rank    = RANK_COLORS[i] || { bg:C.violetLo, border:C.borderHi, text:C.textMd };
        const pct     = Math.round((c.commitCount / maxC) * 100);
        const initial = (c.author || '?').charAt(0).toUpperCase();
        return (
          <div key={i} className="contributor-row fi" style={{
            display:'flex', alignItems:'center', gap:12,
            background:C.raised, border:`1px solid ${C.border}`, borderRadius:12,
            padding:'9px 13px', transition:'background .15s', animationDelay:`${i*0.06}s`,
          }}>
            <div style={{ width:22, height:22, borderRadius:6, background:rank.bg,
              border:`1px solid ${rank.border}`, display:'flex', alignItems:'center',
              justifyContent:'center', fontSize:10, fontWeight:700, color:rank.text, flexShrink:0,
            }}>{i + 1}</div>
            <div style={{ width:30, height:30, borderRadius:'50%', flexShrink:0,
              background:`linear-gradient(135deg, ${rank.text}22, ${rank.text}44)`,
              border:`1px solid ${rank.border}`, display:'flex', alignItems:'center',
              justifyContent:'center', fontSize:12, fontWeight:700, color:rank.text,
            }}>{initial}</div>
            <div style={{ flex:1, minWidth:0 }}>
              <div style={{ fontSize:13.5, fontWeight:600, color:C.text, marginBottom:4,
                overflow:'hidden', textOverflow:'ellipsis', whiteSpace:'nowrap' }}>
                {c.author}
              </div>
              <div style={{ height:3, borderRadius:99, background:C.border, overflow:'hidden' }}>
                <div style={{ '--w':`${pct}%`, width:`${pct}%`, height:'100%', borderRadius:99,
                  background:`linear-gradient(90deg, ${rank.text}bb, ${rank.text})`,
                  animation:'barGrow .6s cubic-bezier(.22,1,.36,1) both',
                  animationDelay:`${0.1 + i * 0.06}s`,
                }} />
              </div>
            </div>
            <div style={{ fontSize:12, fontWeight:700, color:rank.text, background:rank.bg,
              border:`1px solid ${rank.border}`, borderRadius:8, padding:'3px 9px', flexShrink:0,
            }}>{c.commitCount}</div>
          </div>
        );
      })}
    </div>
  );
}

/* ─── Atoms ──────────────────────────────────────────────────── */
const Badge = ({ children, color = C.violet, bg = C.violetLo, border = C.violetMd }) => (
  <span style={{ fontSize:10, fontWeight:700, letterSpacing:'.1em', textTransform:'uppercase',
    padding:'3px 9px', borderRadius:20, background:bg, color, border:`1px solid ${border}` }}>
    {children}
  </span>
);

const Rule = () => (
  <div style={{ height:1, background:`linear-gradient(90deg,transparent,${C.border} 30%,${C.border} 70%,transparent)`, margin:'0.4rem 0' }} />
);

const Label = ({ icon, children }) => (
  <div style={{ display:'flex', alignItems:'center', gap:7, marginBottom:'1rem' }}>
    <span style={{ fontSize:13 }}>{icon}</span>
    <span style={{ fontSize:10.5, fontWeight:700, color:C.textLo, letterSpacing:'.1em', textTransform:'uppercase' }}>
      {children}
    </span>
  </div>
);

/* ─── Button ──────────────────────────────────────────────────── */
function Btn({ onClick, disabled, children, variant = 'primary', accentColor }) {
  const vc = accentColor || C.violet;
  const styles = {
    primary: {
      background: disabled ? C.violetLo : `linear-gradient(135deg, ${vc} 0%, #5244d4 100%)`,
      border: 'none',
      color: disabled ? C.textLo : '#fff',
      boxShadow: disabled ? 'none' : `0 2px 14px rgba(109,93,252,0.28)`,
    },
    ghost: {
      background: C.raised,
      border: `1px solid ${C.border}`,
      color: C.textMd,
      boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
    },
  };
  return (
    <button onClick={onClick} disabled={disabled}
      style={{ display:'inline-flex', alignItems:'center', gap:7, padding:'9px 19px',
        fontSize:12.5, fontWeight:600, borderRadius:10, cursor:disabled?'not-allowed':'pointer',
        transition:'all .16s', whiteSpace:'nowrap', opacity:disabled?.6:1,
        fontFamily:"'Outfit',sans-serif", letterSpacing:'.02em',
        ...styles[variant],
      }}
      onMouseEnter={e => { if (!disabled) { e.currentTarget.style.transform='translateY(-1px)'; if (variant==='primary') e.currentTarget.style.boxShadow=`0 5px 20px rgba(109,93,252,0.40)`; } }}
      onMouseLeave={e => { e.currentTarget.style.transform=''; if (variant==='primary') e.currentTarget.style.boxShadow=styles.primary.boxShadow||'none'; }}
    >{children}</button>
  );
}

/* ─── Health Score Widget ─────────────────────────────────────── */
function HealthScore({ score }) {
  if (score == null) return null;
  let color = C.emerald, bg = C.emeraldLo, label = 'Healthy';
  if (score < 50)      { color = C.red;   bg = 'rgba(229,78,78,0.08)'; label = 'At Risk'; }
  else if (score < 80) { color = C.amber; bg = C.amberLo;              label = 'Needs Work'; }
  return (
    <div className="panel" style={{ padding:'1.3rem 1.8rem', display:'flex', flexDirection:'column',
      alignItems:'center', justifyContent:'center', gap:5, minWidth:150 }}>
      <div style={{ fontSize:10, color:C.textLo, letterSpacing:'.12em', textTransform:'uppercase' }}>
        Health Score
      </div>
      <div style={{ fontSize:42, fontWeight:800, color, fontFamily:"'Fira Code',monospace", lineHeight:1 }}>
        {score}
      </div>
      <Badge color={color} bg={bg} border={color}>{label}</Badge>
    </div>
  );
}

/* ─── Architecture Diagram Viewer ─────────────────────────────── */
function ArchitectureDiagram({ chart }) {
  const containerRef  = useRef(null);
  const [renderError, setRenderError] = useState('');

  useEffect(() => {
    if (!chart || !containerRef.current) return;
    setRenderError('');
    containerRef.current.innerHTML = '';
    const diagramId = 'arch-diagram-' + Date.now();
    mermaid.render(diagramId, chart)
      .then(({ svg }) => {
        if (containerRef.current) containerRef.current.innerHTML = svg;
      })
      .catch(err => {
        console.error('Mermaid render error:', err);
        setRenderError('Could not render diagram. The AI may have returned invalid syntax.');
      });
  }, [chart]);

  if (renderError) {
    return (
      <div style={{ padding:'1rem', textAlign:'center' }}>
        <p style={{ fontSize:12.5, color:C.red, marginBottom:8 }}>⚠ {renderError}</p>
        <pre style={{ fontSize:11, color:C.textMd, textAlign:'left', background:C.raised,
          padding:'1rem', borderRadius:8, overflow:'auto', maxHeight:200, border:`1px solid ${C.border}` }}>
          {chart}
        </pre>
      </div>
    );
  }

  return (
    <div className="mermaid-wrap" ref={containerRef}
      style={{ display:'flex', justifyContent:'center', width:'100%',
        overflowX:'auto', padding:'1rem 0' }} />
  );
}

/* ─── Main App ────────────────────────────────────────────────── */
export default function App() {
  const [url,          setUrl]          = useState('https://github.com/Nagendrasriram/Practice-for-Dsa-using-Java-Arrays');
  const [data,         setData]         = useState(null);
  const [contributors, setContributors] = useState([]);
  const [review,       setReview]       = useState('');
  const [diagramStr,   setDiagramStr]   = useState('');
  const [loading,      setLoading]      = useState(false);
  const [loadingMsg,   setLoadingMsg]   = useState('');
  const [reviewing,    setReviewing]    = useState(false);
  const [mapping,      setMapping]      = useState(false);
  const [error,        setError]        = useState('');
  const [question,     setQuestion]     = useState('');
  const [chatHistory,  setChatHistory]  = useState([]);
  const [chatLoading,  setChatLoading]  = useState(false);
  const chatEnd = useRef(null);

  useEffect(() => {
    chatEnd.current?.scrollIntoView({ behavior:'smooth' });
  }, [chatHistory, chatLoading]);

  const enc = () => encodeURIComponent(url);

  // =========================================================================
  // ANALYZE
  // =========================================================================
  async function analyze() {
    setLoading(true);
    setError('');
    setData(null);
    setReview('');
    setDiagramStr('');
    setChatHistory([]);
    setContributors([]);

    try {
      setLoadingMsg('Cloning and indexing repository…');
      const analyzeRes = await fetch(`${API}/analyze?url=${enc()}`);
      if (!analyzeRes.ok) {
        let errMsg = `HTTP ${analyzeRes.status}`;
        try { const j = await analyzeRes.json(); errMsg = j.error || errMsg; } catch (_) {}
        throw new Error(errMsg);
      }
      await analyzeRes.json();

      setLoadingMsg('Generating AI summary…');
      const summaryRes = await fetch(`${API}/summary?url=${enc()}`);
      if (!summaryRes.ok) {
        let errMsg = `Summary fetch failed — HTTP ${summaryRes.status}`;
        try { const j = await summaryRes.json(); errMsg = j.error || errMsg; } catch (_) {}
        throw new Error(errMsg);
      }
      setData(await summaryRes.json());

      setLoadingMsg('Loading contributors…');
      try {
        const cRes = await fetch(`http://localhost:8080/api/contributors?url=${enc()}`);
        if (cRes.ok) {
          const cData = await cRes.json();
          setContributors(Array.isArray(cData) ? cData : []);
        }
      } catch (_) {}

    } catch (err) {
      setError(`Analysis failed — ${err.message || 'Is your Spring Boot server running on :8080?'}`);
    } finally {
      setLoading(false);
      setLoadingMsg('');
    }
  }

  // =========================================================================
  // CODE REVIEW
  // =========================================================================
  async function runReview() {
    setReviewing(true);
    setReview('');
    try {
      const res = await fetch(`${API}/code-review?url=${enc()}`);
      if (!res.ok) throw new Error(await res.text().catch(() => `HTTP ${res.status}`));
      setReview(await res.text());
    } catch (err) {
      setError(`Code review failed — ${err.message || 'Check IntelliJ console.'}`);
    } finally {
      setReviewing(false);
    }
  }

  // =========================================================================
  // ARCHITECTURE DIAGRAM
  // =========================================================================
  async function generateDiagram() {
    setMapping(true);
    setDiagramStr('');
    try {
      const res = await fetch(`${API}/architecture?url=${enc()}`);
      if (!res.ok) {
        let errMsg = `HTTP ${res.status}`;
        try { const j = await res.json(); errMsg = j.error || errMsg; } catch (_) {}
        throw new Error(errMsg);
      }
      const resData = await res.json();
      const cleanChart = resData.diagram
        .replace(/```mermaid/gi, '')
        .replace(/```/g, '')
        .trim();
      setDiagramStr(cleanChart);
    } catch (err) {
      setError(`Architecture map failed — ${err.message || 'Check IntelliJ console.'}`);
    } finally {
      setMapping(false);
    }
  }

  // =========================================================================
  // RAG CHAT
  // =========================================================================
  async function askChat() {
    if (!question.trim()) return;
    const userQ = question;
    setQuestion('');
    setChatHistory(prev => [...prev, { role:'user', content:userQ }]);
    setChatLoading(true);
    try {
      const res = await fetch('http://localhost:8080/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ question: userQ, url }),
      });
      if (!res.ok) throw new Error(await res.text().catch(() => `HTTP ${res.status}`));
      const answer = await res.text();
      setChatHistory(prev => [...prev, { role:'ai', content: answer }]);
    } catch (err) {
      setChatHistory(prev => [...prev, {
        role:'ai',
        content:`⚠ Chat failed: ${err.message || 'Ensure backend is running.'}`,
      }]);
    } finally {
      setChatLoading(false);
    }
  }

  return (
    <>
      <style>{STYLES}</style>

      {/* ─── Ambient background orbs ─── */}
      <div style={{ position:'fixed', inset:0, pointerEvents:'none', zIndex:0, overflow:'hidden' }}>
        <div style={{ position:'absolute', top:'-8%', right:'8%', width:500, height:500, borderRadius:'50%',
          background:'radial-gradient(circle, rgba(109,93,252,0.09) 0%, transparent 68%)',
          animation:'floatA 12s ease-in-out infinite' }} />
        <div style={{ position:'absolute', bottom:'-5%', left:'-6%', width:380, height:380, borderRadius:'50%',
          background:'radial-gradient(circle, rgba(14,168,119,0.07) 0%, transparent 68%)',
          animation:'floatB 15s ease-in-out infinite' }} />
        <div style={{ position:'absolute', top:'45%', left:'38%', width:280, height:280, borderRadius:'50%',
          background:'radial-gradient(circle, rgba(217,119,6,0.05) 0%, transparent 65%)',
          animation:'floatA 19s ease-in-out infinite reverse' }} />
      </div>

      <div style={{ minHeight:'100vh', padding:'3rem 2rem 8rem', position:'relative', zIndex:1,
        fontFamily:"'Outfit', sans-serif", maxWidth:1200, margin:'0 auto' }}>

        {/* ─── Header ─── */}
        <header style={{ marginBottom:'2.8rem' }} className="fu">
          <div style={{ display:'flex', alignItems:'center', gap:13, marginBottom:10 }}>
            <div style={{ width:44, height:44, borderRadius:14, flexShrink:0,
              background:`linear-gradient(145deg, ${C.violet}, #4f3dd4)`,
              display:'flex', alignItems:'center', justifyContent:'center',
              boxShadow:'0 6px 22px rgba(109,93,252,0.32)',
            }}>
              <svg width="22" height="22" viewBox="0 0 20 20" fill="none">
                <circle cx="10" cy="10" r="3.8" fill="white" fillOpacity=".95"/>
                <circle cx="10" cy="10" r="7.5" stroke="white" strokeOpacity=".4" strokeWidth="1.1"/>
                <circle cx="10" cy="10" r="4.8" stroke="white" strokeOpacity=".2" strokeWidth="3"/>
                {[[10,1.5,10,4],[10,16,10,18.5],[1.5,10,4,10],[16,10,18.5,10]].map(([x1,y1,x2,y2],i) => (
                  <line key={i} x1={x1} y1={y1} x2={x2} y2={y2}
                    stroke="white" strokeOpacity=".6" strokeWidth="1.1" strokeLinecap="round"/>
                ))}
              </svg>
            </div>
            <h1 style={{ fontSize:30, fontWeight:800, color:C.text, letterSpacing:'-.035em' }}>GitInsight</h1>
            <Badge>Beta</Badge>
          </div>
          <p style={{ fontSize:14, color:C.textMd, paddingLeft:57, letterSpacing:'.01em' }}>
            AI-powered codebase health · commit analytics · architecture mapping
          </p>
        </header>

        {/* ─── URL Input ─── */}
        <div className="panel fu" style={{ padding:'1.4rem 1.6rem', marginBottom:'1rem', animationDelay:'.04s' }}>
          <div style={{ fontSize:10, color:C.textLo, letterSpacing:'.12em', textTransform:'uppercase', marginBottom:9 }}>
            GitHub Repository URL
          </div>
          <div style={{ display:'flex', gap:10 }}>
            <div style={{ flex:1, position:'relative' }}>
              <span style={{ position:'absolute', left:13, top:'50%', transform:'translateY(-50%)',
                color:C.textLo, fontSize:14, pointerEvents:'none' }}>⌥</span>
              <input value={url} onChange={e => setUrl(e.target.value)}
                onKeyDown={e => e.key === 'Enter' && analyze()}
                placeholder="https://github.com/user/repo"
                style={{ width:'100%', background:C.raised, border:`1px solid ${C.border}`, borderRadius:10,
                  padding:'11px 14px 11px 32px', fontSize:13, color:C.text, outline:'none',
                  fontFamily:"'Fira Code', monospace", transition:'border-color .2s, box-shadow .2s',
                }}
                onFocus={e => { e.target.style.borderColor = C.violet; e.target.style.boxShadow = `0 0 0 3px ${C.violetLo}`; }}
                onBlur={e  => { e.target.style.borderColor = C.border;  e.target.style.boxShadow = 'none'; }}
              />
            </div>
            <Btn onClick={analyze} disabled={loading}>
              {loading
                ? <><span className="spin" />{loadingMsg || 'Analyzing…'}</>
                : <>✦ Generate Insight</>}
            </Btn>
          </div>
        </div>

        {/* ─── Error ─── */}
        {error && (
          <div className="fi" style={{ background:'rgba(229,78,78,0.07)', border:'1px solid rgba(229,78,78,0.22)',
            borderRadius:12, padding:'11px 16px', fontSize:13, color:C.red,
            marginBottom:'1rem', display:'flex', alignItems:'center', gap:8 }}>
            <span>⚠</span>{error}
          </div>
        )}

        {/* ─── Loading skeletons ─── */}
        {loading && (
          <div style={{ display:'flex', flexDirection:'column', gap:'1rem' }} className="fi">
            <div style={{ fontSize:13, color:C.textMd, textAlign:'center', marginBottom:4, letterSpacing:'.04em' }}>
              {loadingMsg}
            </div>
            {[180, 140, 200].map((h, i) => (
              <div key={i} className="skeleton" style={{ height:h, animationDelay:`${i*0.1}s` }} />
            ))}
          </div>
        )}

        {/* ─── Dashboard ─── */}
        {data && !loading && (
          <div style={{ display:'flex', flexDirection:'column', gap:'1rem' }}>

            {/* Row 1: repo name + commit count + health score */}
            <div style={{ display:'grid', gridTemplateColumns:'1fr auto auto', gap:'1rem',
              animation:'fadeUp .4s cubic-bezier(.22,1,.36,1) both', animationDelay:'.05s' }}>
              <div className="panel" style={{ padding:'1.4rem 1.7rem' }}>
                <div style={{ fontSize:10, color:C.textLo, letterSpacing:'.12em', textTransform:'uppercase', marginBottom:7 }}>
                  Repository
                </div>
                <div style={{ fontSize:21, fontWeight:700, color:C.text, fontFamily:"'Fira Code',monospace",
                  overflow:'hidden', textOverflow:'ellipsis', whiteSpace:'nowrap' }}>
                  {data.repositoryName || url.split('/').pop()}
                </div>
                <div style={{ fontSize:12, color:C.textLo, marginTop:5, fontFamily:"'Fira Code',monospace" }}>
                  {url.replace('https://github.com/', '')}
                </div>
              </div>

              <div className="panel" style={{ padding:'1.3rem 1.8rem', display:'flex', flexDirection:'column',
                alignItems:'center', justifyContent:'center', gap:5, minWidth:150 }}>
                <div style={{ fontSize:10, color:C.textLo, letterSpacing:'.12em', textTransform:'uppercase' }}>
                  Total Commits
                </div>
                <div style={{ fontSize:42, fontWeight:800, color:C.text, fontFamily:"'Fira Code',monospace", lineHeight:1 }}>
                  {data.totalCommits ?? '—'}
                </div>
              </div>

              <HealthScore score={data.healthScore} />
            </div>

            {/* Row 2: AI summary (3fr) + heatmap + contributors (2fr) */}
            <div style={{ display:'grid', gridTemplateColumns:'3fr 2fr', gap:'1rem',
              animation:'fadeUp .4s cubic-bezier(.22,1,.36,1) both', animationDelay:'.09s' }}>

              {/* AI Summary — full Markdown rendering */}
              <div className="panel" style={{ padding:'1.6rem 1.8rem', borderLeft:`3px solid ${C.violet}` }}>
                <Label icon="✦">AI Summary</Label>
                <div className="md">
                  <ReactMarkdown>{cleanMd(data.aiSummary || 'No summary available.')}</ReactMarkdown>
                </div>
              </div>

              {/* Right column: heatmap + contributors */}
              <div style={{ display:'flex', flexDirection:'column', gap:'1rem' }}>
                <div className="panel" style={{ padding:'1.4rem 1.5rem' }}>
                  <Label icon="◫">30-Day Activity</Label>
                  <Heatmap dailyCounts={data.dailyCommitCounts} />
                </div>
                {contributors.length > 0 && (
                  <div className="panel fu" style={{ padding:'1.4rem 1.5rem', animationDelay:'.14s' }}>
                    <Label icon="★">Top Contributors</Label>
                    <Contributors list={contributors} />
                  </div>
                )}
              </div>
            </div>

            <Rule />

            {/* ─── Deep Code Review ─── */}
            {!review ? (
              <div className="panel fu" style={{ display:'flex', justifyContent:'space-between',
                alignItems:'center', padding:'1.3rem 1.7rem', animationDelay:'.13s' }}>
                <div>
                  <div style={{ fontSize:14.5, fontWeight:700, color:C.text, marginBottom:5 }}>Deep Code Review</div>
                  <div style={{ fontSize:13, color:C.textMd }}>Big-O complexity · bug detection · clean-code violations</div>
                </div>
                <Btn onClick={runReview} disabled={reviewing} variant="ghost">
                  {reviewing ? <><span className="spin" />Analysing…</> : <>⌕ Run Analysis</>}
                </Btn>
              </div>
            ) : (
              <div className="panel fu" style={{ padding:'1.6rem', borderTop:`3px solid ${C.violet}` }}>
                <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center',
                  marginBottom:'1.2rem', paddingBottom:'1rem', borderBottom:`1px solid ${C.border}` }}>
                  <div style={{ display:'flex', alignItems:'center', gap:10 }}>
                    <div style={{ width:34, height:34, borderRadius:10, background:C.violetLo,
                      border:`1px solid ${C.violetMd}`, display:'flex', alignItems:'center',
                      justifyContent:'center', fontSize:16 }}>👨‍💻</div>
                    <span style={{ fontSize:14.5, fontWeight:700, color:C.text }}>Senior Engineer Feedback</span>
                  </div>
                  <Btn onClick={runReview} disabled={reviewing} variant="ghost">↺ Re-analyse</Btn>
                </div>
                <div className="md"><ReactMarkdown>{cleanMd(review)}</ReactMarkdown></div>
              </div>
            )}

            <Rule />

            {/* ─── Architecture Diagram ─── */}
            <div className="panel fu" style={{ padding:'1.6rem', borderTop:`3px solid ${C.amber}`, animationDelay:'.15s' }}>
              <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center',
                marginBottom: diagramStr ? '1.2rem' : 0,
                paddingBottom: diagramStr ? '1rem' : 0,
                borderBottom: diagramStr ? `1px solid ${C.border}` : 'none',
              }}>
                <div style={{ display:'flex', alignItems:'center', gap:10 }}>
                  <div style={{ width:34, height:34, borderRadius:10, background:C.amberLo,
                    border:`1px solid rgba(217,119,6,0.25)`, display:'flex',
                    alignItems:'center', justifyContent:'center', fontSize:16 }}>🗺️</div>
                  <div>
                    <span style={{ fontSize:14.5, fontWeight:700, color:C.text, display:'block' }}>
                      System Architecture
                    </span>
                    <span style={{ fontSize:12, color:C.textMd }}>AI-generated dependency flow map</span>
                  </div>
                </div>
                <div style={{ display:'flex', gap:8 }}>
                  {diagramStr && (
                    <Btn onClick={() => setDiagramStr('')} variant="ghost">✕ Clear</Btn>
                  )}
                  <Btn onClick={generateDiagram} disabled={mapping} variant="ghost" accentColor={C.amber}>
                    {mapping
                      ? <><span className="spin" style={{ borderTopColor:C.amber, borderColor:C.amberLo }} />Mapping…</>
                      : <>⌕ Map Architecture</>}
                  </Btn>
                </div>
              </div>

              {mapping && (
                <div style={{ display:'flex', alignItems:'center', justifyContent:'center',
                  gap:10, padding:'2.5rem 0' }}>
                  <span className="spin" style={{ borderTopColor:C.amber, borderColor:C.amberLo,
                    width:20, height:20 }} />
                  <span style={{ fontSize:13, color:C.textMd }}>Compiling architecture map…</span>
                </div>
              )}

              {diagramStr && !mapping && (
                <div style={{ background:C.raised, borderRadius:12, padding:'1rem',
                  border:`1px solid ${C.border}` }}>
                  <ArchitectureDiagram chart={diagramStr} />
                </div>
              )}

              {diagramStr && !mapping && (
                <details style={{ marginTop:'0.8rem' }}>
                  <summary style={{ fontSize:12, color:C.textLo, cursor:'pointer',
                    letterSpacing:'.05em', userSelect:'none', padding:'4px 0' }}>
                    View raw Mermaid syntax
                  </summary>
                  <pre style={{ marginTop:8, padding:'0.85rem 1rem', background:C.raised,
                    borderRadius:8, border:`1px solid ${C.border}`, fontSize:11,
                    color:C.textMd, overflowX:'auto', lineHeight:1.7 }}>
                    {diagramStr}
                  </pre>
                </details>
              )}
            </div>

            <Rule />

            {/* ─── RAG Chat ─── */}
            <div className="panel fu" style={{ padding:'1.6rem', borderTop:`3px solid ${C.emerald}`, animationDelay:'.16s' }}>
              <div style={{ display:'flex', alignItems:'center', gap:10,
                paddingBottom:'1rem', marginBottom:'.8rem', borderBottom:`1px solid ${C.border}` }}>
                <div style={{ width:34, height:34, borderRadius:10, background:C.emeraldLo,
                  border:`1px solid rgba(14,168,119,0.22)`, display:'flex',
                  alignItems:'center', justifyContent:'center', fontSize:16 }}>🤖</div>
                <span style={{ fontSize:14.5, fontWeight:700, color:C.text }}>Ask the Codebase</span>
                <Badge color={C.emerald} bg={C.emeraldLo} border="rgba(14,168,119,0.25)">RAG</Badge>
              </div>

              <div className="chat-scroll" style={{ maxHeight:400, overflowY:'auto', display:'flex',
                flexDirection:'column', gap:'0.75rem', paddingRight:6, marginBottom:'1rem' }}>
                {chatHistory.length === 0 && !chatLoading && (
                  <div style={{ textAlign:'center', color:C.textLo, fontSize:13, padding:'2rem 0' }}>
                    Try: <em style={{ color:C.textMd }}>"How is the main algorithm implemented?"</em>
                  </div>
                )}
                {chatHistory.map((msg, i) => (
                  <div key={i} className="fi"
                    style={{ alignSelf:msg.role==='user'?'flex-end':'flex-start', maxWidth:'84%' }}>
                    {msg.role === 'user' ? (
                      <div style={{ background:`linear-gradient(135deg, ${C.violet}, #4f3dd4)`,
                        color:'#fff', padding:'10px 16px', borderRadius:'14px 14px 3px 14px',
                        fontSize:13.5, lineHeight:1.65, boxShadow:'0 3px 14px rgba(109,93,252,0.24)' }}>
                        {msg.content}
                      </div>
                    ) : (
                      <div style={{ background:C.raised, border:`1px solid ${C.border}`,
                        color:C.text, padding:'12px 16px', borderRadius:'3px 14px 14px 14px',
                        fontSize:13.5, boxShadow:'0 1px 4px rgba(0,0,0,0.05)' }}>
                        <div className="md"><ReactMarkdown>{cleanMd(msg.content)}</ReactMarkdown></div>
                      </div>
                    )}
                  </div>
                ))}
                {chatLoading && (
                  <div style={{ alignSelf:'flex-start', background:C.raised, border:`1px solid ${C.border}`,
                    padding:'11px 16px', borderRadius:'3px 14px 14px 14px',
                    display:'flex', alignItems:'center', gap:9,
                    boxShadow:'0 1px 4px rgba(0,0,0,0.05)' }}>
                    <span className="spin" style={{ borderTopColor:C.emerald, borderColor:C.emeraldLo }} />
                    <span style={{ fontSize:12.5, color:C.textMd }}>Searching vector database…</span>
                  </div>
                )}
                <div ref={chatEnd} />
              </div>

              <div style={{ display:'flex', gap:9 }}>
                <input value={question} onChange={e => setQuestion(e.target.value)}
                  onKeyDown={e => e.key === 'Enter' && !chatLoading && askChat()}
                  placeholder="Ask anything about this repository…"
                  style={{ flex:1, background:C.raised, border:`1px solid ${C.border}`, borderRadius:10,
                    padding:'11px 16px', fontSize:13.5, color:C.text, outline:'none',
                    fontFamily:"'Outfit',sans-serif", transition:'border-color .2s, box-shadow .2s' }}
                  onFocus={e => { e.target.style.borderColor = C.emerald; e.target.style.boxShadow = `0 0 0 3px ${C.emeraldLo}`; }}
                  onBlur={e  => { e.target.style.borderColor = C.border; e.target.style.boxShadow = 'none'; }}
                />
                <Btn onClick={askChat} disabled={chatLoading || !question.trim()} accentColor={C.emerald}>
                  Send ↗
                </Btn>
              </div>
            </div>

          </div>
        )}
      </div>
    </>
  );
}



// import { useState, useRef, useEffect } from 'react';
// import ReactMarkdown from 'react-markdown';
// import mermaid from 'mermaid';

// mermaid.initialize({
//   startOnLoad: false,
//   theme: 'dark',
//   fontFamily: "'Outfit', sans-serif",
//   themeVariables: {
//     primaryColor: '#8b7fff',
//     primaryTextColor: '#eceef8',
//     primaryBorderColor: '#202540',
//     lineColor: '#7c819c',
//     sectionBkgColor: '#0f1120',
//     altSectionBkgColor: '#0b0d1a',
//     gridColor: '#161929',
//     secondaryColor: '#0f1120',
//     tertiaryColor: '#161929',
//   },
// });

// const API = 'http://localhost:8080/api/repositories';

// /* ─── Markdown sanitizer ─────────────────────────────────────── */
// function cleanMd(text = '') {
//   return text
//     .replace(/\*{3,}([^*]+)\*{3,}/g, '**$1**')
//     .replace(/\*{4,}/g, '')
//     .replace(/^\* /gm, '- ')
//     .replace(/\n{3,}/g, '\n\n')
//     .trim();
// }

// /* ─── Design tokens ─────────────────────────────────────────── */
// const C = {
//   bg:       '#06070f',
//   surface:  '#0b0d1a',
//   raised:   '#0f1120',
//   border:   '#161929',
//   borderHi: '#202540',
//   violet:   '#8b7fff',
//   violetLo: 'rgba(139,127,255,0.10)',
//   violetMd: 'rgba(139,127,255,0.22)',
//   emerald:  '#34d399',
//   emeraldLo:'rgba(52,211,153,0.10)',
//   amber:    '#fbbf24',
//   amberLo:  'rgba(251,191,36,0.10)',
//   text:     '#eceef8',
//   textMd:   '#7c819c',
//   textLo:   '#2e334f',
// };

// const RANK_COLORS = [
//   { bg:'rgba(251,191,36,0.15)', border:'rgba(251,191,36,0.35)', text:'#fbbf24' },
//   { bg:'rgba(148,163,184,0.12)', border:'rgba(148,163,184,0.3)', text:'#94a3b8' },
//   { bg:'rgba(180,120,80,0.12)', border:'rgba(180,120,80,0.3)', text:'#c07050' },
// ];

// /* ─── Global styles ──────────────────────────────────────────── */
// const STYLES = `
//   @import url('https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700;800&family=Fira+Code:wght@400;500&display=swap');
//   *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
//   :root { color-scheme: dark; }
//   body { background: ${C.bg}; }

//   @keyframes fadeUp  { from{opacity:0;transform:translateY(12px)} to{opacity:1;transform:translateY(0)} }
//   @keyframes fadeIn  { from{opacity:0} to{opacity:1} }
//   @keyframes shimmer { from{background-position:-600px 0} to{background-position:600px 0} }
//   @keyframes spin    { to{transform:rotate(360deg)} }
//   @keyframes floatA  { 0%,100%{transform:translate(0,0) scale(1)} 50%{transform:translate(10px,-8px) scale(1.05)} }
//   @keyframes floatB  { 0%,100%{transform:translate(0,0) scale(1)} 50%{transform:translate(-8px,10px) scale(1.04)} }
//   @keyframes barGrow { from{width:0} to{width:var(--w)} }

//   .fu { animation: fadeUp .4s cubic-bezier(.22,1,.36,1) both; }
//   .fi { animation: fadeIn .3s ease both; }

//   .skeleton {
//     background: linear-gradient(90deg, ${C.border} 25%, #181d35 50%, ${C.border} 75%);
//     background-size: 600px 100%;
//     animation: shimmer 1.6s infinite;
//     border-radius: 14px;
//   }
//   .spin {
//     width:15px; height:15px; border-radius:50%;
//     border:2px solid ${C.violetMd}; border-top-color:${C.violet};
//     animation:spin .65s linear infinite; display:inline-block; flex-shrink:0;
//   }

//   .chat-scroll::-webkit-scrollbar { width:3px }
//   .chat-scroll::-webkit-scrollbar-track { background:transparent }
//   .chat-scroll::-webkit-scrollbar-thumb { background:${C.border}; border-radius:3px }

//   .md h1,.md h2,.md h3 { color:${C.text}; font-family:'Outfit',sans-serif; font-weight:700; margin:1.3em 0 .4em }
//   .md h1{font-size:17px} .md h2{font-size:15px} .md h3{font-size:13px}
//   .md p { color:${C.textMd}; font-size:13.5px; line-height:1.85; margin:0 0 .85em }
//   .md code { background:${C.border}; color:#a78bfa; padding:2px 6px; border-radius:5px; font-family:'Fira Code',monospace; font-size:11.5px }
//   .md pre { background:${C.surface}; padding:.85rem 1rem; border-radius:10px; overflow-x:auto; border:1px solid ${C.border}; margin:0 0 1em }
//   .md pre code { background:none; padding:0; color:${C.textMd} }
//   .md ul,.md ol { padding-left:1.35rem; color:${C.textMd}; font-size:13.5px; margin:0 0 .85em }
//   .md li { margin-bottom:5px }
//   .md strong { color:#60a5fa }
//   .md em { color:${C.textMd}; font-style:italic }
//   .md strong em,.md em strong { font-style:normal; color:#60a5fa }
//   .md blockquote { border-left:2px solid ${C.violet}; padding:.4rem 1rem; color:${C.textLo}; margin:0 0 1em }
//   .md ul ul,.md ul ol,.md ol ul,.md ol ol { margin:.3em 0 .3em; padding-left:1.2rem }
//   .md li>strong { color:#60a5fa }
//   .md li>em { color:${C.textMd} }

//   input::placeholder { color:${C.textLo} }
//   input { caret-color:${C.violet} }

//   .panel { background:${C.surface}; border:1px solid ${C.border}; border-radius:18px }
//   .panel:hover { border-color:${C.borderHi}; transition:border-color .2s }

//   .contributor-row:hover { background:${C.raised}!important }

//   .mermaid-wrap svg { max-width:100%; height:auto; }
//   .mermaid-wrap .node rect, .mermaid-wrap .node circle { fill:${C.raised}!important; stroke:${C.borderHi}!important; }
//   .mermaid-wrap .node .label { color:${C.text}!important; }
//   .mermaid-wrap .edgePath .path { stroke:${C.textMd}!important; }
// `;

// /* ─── Commit heatmap ──────────────────────────────────────────── */
// function Heatmap({ dailyCounts }) {
//   const data = Object.entries(dailyCounts || {}).slice(-42);
//   const max  = Math.max(...data.map(([, v]) => v), 1);
//   const color = (n) => !n ? C.border : `rgba(139,127,255,${Math.max(0.14, n / max)})`;
//   const glow  = (n) => n > max * 0.65 ? `0 0 5px rgba(139,127,255,0.45)` : 'none';
//   return (
//     <div>
//       <div style={{ display:'flex', flexWrap:'wrap', gap:3 }}>
//         {data.length ? data.map(([date, count]) => (
//           <div key={date} title={`${date}: ${count} commit${count !== 1 ? 's' : ''}`}
//             style={{ width:13, height:13, borderRadius:3, cursor:'default',
//               transition:'transform .12s, box-shadow .12s',
//               background:color(count),
//               border:`1px solid rgba(139,127,255,${!count ? 0.07 : Math.min(count/max+0.1,0.75)})`,
//               boxShadow:glow(count),
//             }}
//             onMouseEnter={e => { e.target.style.transform='scale(1.45)'; e.target.style.zIndex=5; }}
//             onMouseLeave={e => { e.target.style.transform='scale(1)'; e.target.style.zIndex=''; }}
//           />
//         )) : <p style={{ fontSize:12, color:C.textLo, fontStyle:'italic' }}>No activity data.</p>}
//       </div>
//       <div style={{ display:'flex', alignItems:'center', gap:5, marginTop:9 }}>
//         <span style={{ fontSize:10.5, color:C.textLo }}>less</span>
//         {[0.07,0.18,0.38,0.62,1].map((o, i) => (
//           <div key={i} style={{ width:11, height:11, borderRadius:2, background:`rgba(139,127,255,${o})` }} />
//         ))}
//         <span style={{ fontSize:10.5, color:C.textLo }}>more</span>
//       </div>
//     </div>
//   );
// }

// /* ─── Contributors leaderboard ───────────────────────────────── */
// function Contributors({ list }) {
//   if (!list || !list.length) return null;
//   const maxC = list[0]?.commitCount || 1;
//   return (
//     <div style={{ display:'flex', flexDirection:'column', gap:6 }}>
//       {list.map((c, i) => {
//         const rank    = RANK_COLORS[i] || { bg:C.violetLo, border:C.border, text:C.textMd };
//         const pct     = Math.round((c.commitCount / maxC) * 100);
//         const initial = (c.author || '?').charAt(0).toUpperCase();
//         return (
//           <div key={i} className="contributor-row fi" style={{
//             display:'flex', alignItems:'center', gap:12,
//             background:C.surface, border:`1px solid ${C.border}`, borderRadius:12,
//             padding:'9px 13px', transition:'background .15s', animationDelay:`${i*0.06}s`,
//           }}>
//             <div style={{ width:22, height:22, borderRadius:6, background:rank.bg,
//               border:`1px solid ${rank.border}`, display:'flex', alignItems:'center',
//               justifyContent:'center', fontSize:10, fontWeight:700, color:rank.text, flexShrink:0,
//             }}>{i + 1}</div>
//             <div style={{ width:30, height:30, borderRadius:'50%', flexShrink:0,
//               background:`linear-gradient(135deg, ${rank.text}22, ${rank.text}44)`,
//               border:`1px solid ${rank.border}`, display:'flex', alignItems:'center',
//               justifyContent:'center', fontSize:12, fontWeight:700, color:rank.text,
//             }}>{initial}</div>
//             <div style={{ flex:1, minWidth:0 }}>
//               <div style={{ fontSize:13.5, fontWeight:600, color:C.text, marginBottom:4,
//                 overflow:'hidden', textOverflow:'ellipsis', whiteSpace:'nowrap' }}>
//                 {c.author}
//               </div>
//               <div style={{ height:3, borderRadius:99, background:C.border, overflow:'hidden' }}>
//                 <div style={{ '--w':`${pct}%`, width:`${pct}%`, height:'100%', borderRadius:99,
//                   background:`linear-gradient(90deg, ${rank.text}bb, ${rank.text})`,
//                   animation:'barGrow .6s cubic-bezier(.22,1,.36,1) both',
//                   animationDelay:`${0.1 + i * 0.06}s`,
//                 }} />
//               </div>
//             </div>
//             <div style={{ fontSize:12, fontWeight:700, color:rank.text, background:rank.bg,
//               border:`1px solid ${rank.border}`, borderRadius:8, padding:'3px 9px', flexShrink:0,
//             }}>{c.commitCount}</div>
//           </div>
//         );
//       })}
//     </div>
//   );
// }

// /* ─── Atoms ──────────────────────────────────────────────────── */
// const Badge = ({ children, color = C.violet, bg = C.violetLo, border = C.violetMd }) => (
//   <span style={{ fontSize:10, fontWeight:700, letterSpacing:'.1em', textTransform:'uppercase',
//     padding:'3px 9px', borderRadius:20, background:bg, color, border:`1px solid ${border}` }}>
//     {children}
//   </span>
// );

// const Rule = () => (
//   <div style={{ height:1, background:`linear-gradient(90deg,transparent,${C.border} 30%,${C.border} 70%,transparent)` }} />
// );

// const Label = ({ icon, children }) => (
//   <div style={{ display:'flex', alignItems:'center', gap:7, marginBottom:'1rem' }}>
//     <span style={{ fontSize:13 }}>{icon}</span>
//     <span style={{ fontSize:10.5, fontWeight:700, color:C.textMd, letterSpacing:'.1em', textTransform:'uppercase' }}>
//       {children}
//     </span>
//   </div>
// );

// /* ─── Button ──────────────────────────────────────────────────── */
// function Btn({ onClick, disabled, children, variant = 'primary', accentColor }) {
//   const vc = accentColor || C.violet;
//   const styles = {
//     primary: {
//       background: disabled ? C.violetLo : `linear-gradient(135deg, ${vc} 0%, #6857e8 100%)`,
//       border: 'none', color: '#fff',
//       boxShadow: disabled ? 'none' : `0 2px 12px rgba(139,127,255,0.3)`,
//     },
//     ghost: {
//       background: 'transparent', border: `1px solid ${C.borderHi}`, color: C.textMd,
//     },
//   };
//   return (
//     <button onClick={onClick} disabled={disabled}
//       style={{ display:'inline-flex', alignItems:'center', gap:7, padding:'9px 19px',
//         fontSize:12.5, fontWeight:600, borderRadius:10, cursor:disabled?'not-allowed':'pointer',
//         transition:'all .16s', whiteSpace:'nowrap', opacity:disabled?.55:1,
//         fontFamily:"'Outfit',sans-serif", letterSpacing:'.02em',
//         ...styles[variant],
//       }}
//       onMouseEnter={e => { if (!disabled) { e.currentTarget.style.transform='translateY(-1px)'; if (variant==='primary') e.currentTarget.style.boxShadow=`0 4px 18px rgba(139,127,255,0.45)`; } }}
//       onMouseLeave={e => { e.currentTarget.style.transform=''; if (variant==='primary') e.currentTarget.style.boxShadow=styles.primary.boxShadow||'none'; }}
//     >{children}</button>
//   );
// }

// /* ─── Health Score Widget ─────────────────────────────────────── */
// function HealthScore({ score }) {
//   if (score == null) return null;
//   let color = C.emerald, bg = C.emeraldLo, label = 'Healthy';
//   if (score < 50)      { color = '#ef4444'; bg = 'rgba(239,68,68,0.1)';  label = 'At Risk'; }
//   else if (score < 80) { color = C.amber;   bg = C.amberLo;              label = 'Needs Work'; }
//   return (
//     <div className="panel" style={{ padding:'1.3rem 1.8rem', display:'flex', flexDirection:'column',
//       alignItems:'center', justifyContent:'center', gap:5, minWidth:140 }}>
//       <div style={{ fontSize:10, color:C.textLo, letterSpacing:'.12em', textTransform:'uppercase' }}>
//         Health Score
//       </div>
//       <div style={{ fontSize:42, fontWeight:800, color, fontFamily:"'Fira Code',monospace", lineHeight:1,
//         textShadow:`0 0 15px ${color}40` }}>
//         {score}
//       </div>
//       <Badge color={color} bg={bg} border={color}>{label}</Badge>
//     </div>
//   );
// }

// /* ─── Architecture Diagram Viewer ─────────────────────────────── */
// function ArchitectureDiagram({ chart }) {
//   const containerRef  = useRef(null);
//   const [renderError, setRenderError] = useState('');

//   useEffect(() => {
//     if (!chart || !containerRef.current) return;
//     setRenderError('');
//     containerRef.current.innerHTML = '';

//     const diagramId = 'arch-diagram-' + Date.now();

//     mermaid.render(diagramId, chart)
//       .then(({ svg }) => {
//         if (containerRef.current) {
//           containerRef.current.innerHTML = svg;
//         }
//       })
//       .catch(err => {
//         console.error('Mermaid render error:', err);
//         setRenderError('Could not render diagram. The AI may have returned invalid syntax.');
//       });
//   }, [chart]);

//   if (renderError) {
//     return (
//       <div style={{ padding:'1rem', textAlign:'center' }}>
//         <p style={{ fontSize:12.5, color:'#fca5a5', marginBottom:8 }}>⚠ {renderError}</p>
//         <pre style={{ fontSize:11, color:C.textMd, textAlign:'left', background:C.bg,
//           padding:'1rem', borderRadius:8, overflow:'auto', maxHeight:200 }}>
//           {chart}
//         </pre>
//       </div>
//     );
//   }

//   return (
//     <div className="mermaid-wrap" ref={containerRef}
//       style={{ display:'flex', justifyContent:'center', width:'100%',
//         overflowX:'auto', padding:'1rem 0' }} />
//   );
// }

// /* ─── Main App ────────────────────────────────────────────────── */
// export default function App() {
//   const [url,          setUrl]         = useState('https://github.com/Nagendrasriram/Practice-for-Dsa-using-Java-Arrays');
//   const [data,         setData]        = useState(null);
//   const [contributors, setContributors]= useState([]);
//   const [review,       setReview]      = useState('');
//   const [diagramStr,   setDiagramStr]  = useState('');
//   const [loading,      setLoading]     = useState(false);
//   const [loadingMsg,   setLoadingMsg]  = useState('');
//   const [reviewing,    setReviewing]   = useState(false);
//   const [mapping,      setMapping]     = useState(false);
//   const [error,        setError]       = useState('');
//   const [question,     setQuestion]    = useState('');
//   const [chatHistory,  setChatHistory] = useState([]);
//   const [chatLoading,  setChatLoading] = useState(false);
//   const chatEnd = useRef(null);

//   useEffect(() => {
//     chatEnd.current?.scrollIntoView({ behavior:'smooth' });
//   }, [chatHistory, chatLoading]);

//   const enc = () => encodeURIComponent(url);

//   // =========================================================================
//   // ANALYZE — clone + index + summary + contributors
//   // =========================================================================
//   async function analyze() {
//     setLoading(true);
//     setError('');
//     setData(null);
//     setReview('');
//     setDiagramStr('');
//     setChatHistory([]);
//     setContributors([]);

//     try {
//       // Step 1: Clone + Index + Save
//       setLoadingMsg('Cloning and indexing repository…');
//       const analyzeRes = await fetch(`${API}/analyze?url=${enc()}`);
//       if (!analyzeRes.ok) {
//         let errMsg = `HTTP ${analyzeRes.status}`;
//         try { const j = await analyzeRes.json(); errMsg = j.error || errMsg; } catch (_) {}
//         throw new Error(errMsg);
//       }
//       await analyzeRes.json();

//       // Step 2: Fetch Dashboard Summary
//       setLoadingMsg('Generating AI summary…');
//       const summaryRes = await fetch(`${API}/summary?url=${enc()}`);
//       if (!summaryRes.ok) {
//         let errMsg = `Summary fetch failed — HTTP ${summaryRes.status}`;
//         try { const j = await summaryRes.json(); errMsg = j.error || errMsg; } catch (_) {}
//         throw new Error(errMsg);
//       }
//       setData(await summaryRes.json());

//       // Step 3: Contributors — non-fatal
//       setLoadingMsg('Loading contributors…');
//       try {
//         const cRes = await fetch(`http://localhost:8080/api/contributors?url=${enc()}`);
//         if (cRes.ok) {
//           const cData = await cRes.json();
//           setContributors(Array.isArray(cData) ? cData : []);
//         }
//       } catch (_) {}

//     } catch (err) {
//       setError(`Analysis failed — ${err.message || 'Is your Spring Boot server running on :8080?'}`);
//     } finally {
//       setLoading(false);
//       setLoadingMsg('');
//     }
//   }

//   // =========================================================================
//   // CODE REVIEW
//   // =========================================================================
//   async function runReview() {
//     setReviewing(true);
//     setReview('');
//     try {
//       const res = await fetch(`${API}/code-review?url=${enc()}`);
//       if (!res.ok) throw new Error(await res.text().catch(() => `HTTP ${res.status}`));
//       setReview(await res.text());
//     } catch (err) {
//       setError(`Code review failed — ${err.message || 'Check IntelliJ console.'}`);
//     } finally {
//       setReviewing(false);
//     }
//   }

//   // =========================================================================
//   // ARCHITECTURE DIAGRAM
//   // =========================================================================
//   async function generateDiagram() {
//     setMapping(true);
//     setDiagramStr('');
//     try {
//       const res = await fetch(`${API}/architecture?url=${enc()}`);
//       if (!res.ok) {
//         let errMsg = `HTTP ${res.status}`;
//         try { const j = await res.json(); errMsg = j.error || errMsg; } catch (_) {}
//         throw new Error(errMsg);
//       }
//       const resData = await res.json();

//       // Strip any markdown backticks the AI adds despite instructions
//       const cleanChart = resData.diagram
//         .replace(/```mermaid/gi, '')
//         .replace(/```/g, '')
//         .trim();

//       setDiagramStr(cleanChart);
//     } catch (err) {
//       setError(`Architecture map failed — ${err.message || 'Check IntelliJ console.'}`);
//     } finally {
//       setMapping(false);
//     }
//   }

//   // =========================================================================
//   // RAG CHAT
//   // =========================================================================
//   async function askChat() {
//     if (!question.trim()) return;
//     const userQ = question;
//     setQuestion('');
//     setChatHistory(prev => [...prev, { role:'user', content:userQ }]);
//     setChatLoading(true);
//     try {
//       const res = await fetch('http://localhost:8080/api/chat', {
//         method: 'POST',
//         headers: { 'Content-Type': 'application/json' },
//         body: JSON.stringify({ question: userQ, url }),
//       });
//       if (!res.ok) throw new Error(await res.text().catch(() => `HTTP ${res.status}`));

//       // ✅ Read text FIRST, then pass into state updater — fixes await-in-arrow error
//       const answer = await res.text();
//       setChatHistory(prev => [...prev, { role:'ai', content: answer }]);

//     } catch (err) {
//       setChatHistory(prev => [...prev, {
//         role:'ai',
//         content:`⚠ Chat failed: ${err.message || 'Ensure backend is running.'}`,
//       }]);
//     } finally {
//       setChatLoading(false);
//     }
//   }

//   return (
//     <>
//       <style>{STYLES}</style>

//       {/* ─── Ambient background orbs ─── */}
//       <div style={{ position:'fixed', inset:0, pointerEvents:'none', zIndex:0, overflow:'hidden' }}>
//         <div style={{ position:'absolute', top:'-10%', right:'10%', width:480, height:480, borderRadius:'50%',
//           background:'radial-gradient(circle, rgba(139,127,255,0.065) 0%, transparent 68%)',
//           animation:'floatA 10s ease-in-out infinite' }} />
//         <div style={{ position:'absolute', bottom:'-5%', left:'-8%', width:360, height:360, borderRadius:'50%',
//           background:'radial-gradient(circle, rgba(52,211,153,0.045) 0%, transparent 68%)',
//           animation:'floatB 13s ease-in-out infinite' }} />
//         <div style={{ position:'absolute', top:'40%', left:'40%', width:260, height:260, borderRadius:'50%',
//           background:'radial-gradient(circle, rgba(251,191,36,0.025) 0%, transparent 65%)',
//           animation:'floatA 17s ease-in-out infinite reverse' }} />
//       </div>

//       <div style={{ minHeight:'100vh', padding:'3rem 1.5rem 8rem', position:'relative', zIndex:1,
//         fontFamily:"'Outfit', sans-serif", maxWidth:920, margin:'0 auto' }}>

//         {/* ─── Header ─── */}
//         <header style={{ marginBottom:'2.8rem' }} className="fu">
//           <div style={{ display:'flex', alignItems:'center', gap:12, marginBottom:10 }}>
//             <div style={{ width:40, height:40, borderRadius:12, flexShrink:0,
//               background:`linear-gradient(145deg, ${C.violet}, #5b4de8)`,
//               display:'flex', alignItems:'center', justifyContent:'center',
//               boxShadow:'0 4px 18px rgba(139,127,255,0.38)',
//             }}>
//               <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
//                 <circle cx="10" cy="10" r="3.8" fill="white" fillOpacity=".92"/>
//                 <circle cx="10" cy="10" r="7.5" stroke="white" strokeOpacity=".35" strokeWidth="1.1"/>
//                 <circle cx="10" cy="10" r="4.8" stroke="white" strokeOpacity=".18" strokeWidth="3"/>
//                 {[[10,1.5,10,4],[10,16,10,18.5],[1.5,10,4,10],[16,10,18.5,10]].map(([x1,y1,x2,y2],i) => (
//                   <line key={i} x1={x1} y1={y1} x2={x2} y2={y2}
//                     stroke="white" strokeOpacity=".55" strokeWidth="1.1" strokeLinecap="round"/>
//                 ))}
//               </svg>
//             </div>
//             <h1 style={{ fontSize:28, fontWeight:800, color:C.text, letterSpacing:'-.035em' }}>GitInsight</h1>
//             <Badge>Beta</Badge>
//           </div>
//           <p style={{ fontSize:13.5, color:C.textMd, paddingLeft:52, letterSpacing:'.01em' }}>
//             AI-powered codebase health · commit analytics · static review
//           </p>
//         </header>

//         {/* ─── URL Input ─── */}
//         <div className="panel fu" style={{ padding:'1.3rem 1.5rem', marginBottom:'1rem', animationDelay:'.04s' }}>
//           <div style={{ fontSize:10, color:C.textLo, letterSpacing:'.12em', textTransform:'uppercase', marginBottom:9 }}>
//             Repository URL
//           </div>
//           <div style={{ display:'flex', gap:10 }}>
//             <div style={{ flex:1, position:'relative' }}>
//               <span style={{ position:'absolute', left:12, top:'50%', transform:'translateY(-50%)',
//                 color:C.textLo, fontSize:14, pointerEvents:'none' }}>⌥</span>
//               <input value={url} onChange={e => setUrl(e.target.value)}
//                 onKeyDown={e => e.key === 'Enter' && analyze()}
//                 placeholder="https://github.com/user/repo"
//                 style={{ width:'100%', background:C.bg, border:`1px solid ${C.border}`, borderRadius:10,
//                   padding:'10px 14px 10px 30px', fontSize:12.5, color:C.text, outline:'none',
//                   fontFamily:"'Fira Code', monospace", transition:'border-color .2s',
//                 }}
//                 onFocus={e => e.target.style.borderColor = C.violet}
//                 onBlur={e  => e.target.style.borderColor = C.border}
//               />
//             </div>
//             <Btn onClick={analyze} disabled={loading}>
//               {loading
//                 ? <><span className="spin" />{loadingMsg || 'Analyzing…'}</>
//                 : <>✦ Generate Insight</>}
//             </Btn>
//           </div>
//         </div>

//         {/* ─── Error ─── */}
//         {error && (
//           <div className="fi" style={{ background:'rgba(239,68,68,0.07)', border:'1px solid rgba(239,68,68,0.22)',
//             borderRadius:12, padding:'11px 16px', fontSize:13, color:'#fca5a5',
//             marginBottom:'1rem', display:'flex', alignItems:'center', gap:8 }}>
//             <span>⚠</span>{error}
//           </div>
//         )}

//         {/* ─── Loading skeletons ─── */}
//         {loading && (
//           <div style={{ display:'flex', flexDirection:'column', gap:'1rem' }} className="fi">
//             <div style={{ fontSize:12.5, color:C.textMd, textAlign:'center', marginBottom:4, letterSpacing:'.04em' }}>
//               {loadingMsg}
//             </div>
//             {[180, 140, 200].map((h, i) => (
//               <div key={i} className="skeleton" style={{ height:h, animationDelay:`${i*0.1}s` }} />
//             ))}
//           </div>
//         )}

//         {/* ─── Dashboard ─── */}
//         {data && !loading && (
//           <div style={{ display:'flex', flexDirection:'column', gap:'1rem' }}>

//             {/* Row 1: repo name + commit count + health score */}
//             <div style={{ display:'grid', gridTemplateColumns:'1fr auto auto', gap:'1rem',
//               animation:'fadeUp .4s cubic-bezier(.22,1,.36,1) both', animationDelay:'.05s' }}>
//               <div className="panel" style={{ padding:'1.3rem 1.6rem' }}>
//                 <div style={{ fontSize:10, color:C.textLo, letterSpacing:'.12em', textTransform:'uppercase', marginBottom:7 }}>
//                   Repository
//                 </div>
//                 <div style={{ fontSize:20, fontWeight:700, color:C.text, fontFamily:"'Fira Code',monospace",
//                   overflow:'hidden', textOverflow:'ellipsis', whiteSpace:'nowrap' }}>
//                   {data.repositoryName || url.split('/').pop()}
//                 </div>
//                 <div style={{ fontSize:11.5, color:C.textLo, marginTop:5, fontFamily:"'Fira Code',monospace" }}>
//                   {url.replace('https://github.com/', '')}
//                 </div>
//               </div>

//               <div className="panel" style={{ padding:'1.3rem 1.8rem', display:'flex', flexDirection:'column',
//                 alignItems:'center', justifyContent:'center', gap:5, minWidth:140 }}>
//                 <div style={{ fontSize:10, color:C.textLo, letterSpacing:'.12em', textTransform:'uppercase' }}>
//                   Total Commits
//                 </div>
//                 <div style={{ fontSize:42, fontWeight:800, color:C.text, fontFamily:"'Fira Code',monospace", lineHeight:1 }}>
//                   {data.totalCommits ?? '—'}
//                 </div>
//               </div>

//               <HealthScore score={data.healthScore} />
//             </div>

//             {/* Row 2: AI summary + heatmap + contributors */}
//             <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr', gap:'1rem',
//               animation:'fadeUp .4s cubic-bezier(.22,1,.36,1) both', animationDelay:'.09s' }}>
//               <div className="panel" style={{ padding:'1.4rem 1.5rem', borderLeft:`2px solid ${C.violet}` }}>
//                 <Label icon="✦">AI Summary</Label>
//                 <p style={{ fontSize:13.5, color:C.textMd, lineHeight:1.85, margin:0 }}>
//                   {data.aiSummary || 'No summary available.'}
//                 </p>
//               </div>

//               <div style={{ display:'flex', flexDirection:'column', gap:'1rem' }}>
//                 <div className="panel" style={{ padding:'1.4rem 1.5rem' }}>
//                   <Label icon="◫">30-Day Activity</Label>
//                   <Heatmap dailyCounts={data.dailyCommitCounts} />
//                 </div>
//                 {contributors.length > 0 && (
//                   <div className="panel fu" style={{ padding:'1.4rem 1.5rem', animationDelay:'.14s' }}>
//                     <Label icon="★">Top Contributors</Label>
//                     <Contributors list={contributors} />
//                   </div>
//                 )}
//               </div>
//             </div>

//             <Rule />

//             {/* ─── Deep Code Review ─── */}
//             {!review ? (
//               <div className="panel fu" style={{ display:'flex', justifyContent:'space-between',
//                 alignItems:'center', padding:'1.25rem 1.6rem', animationDelay:'.13s' }}>
//                 <div>
//                   <div style={{ fontSize:14.5, fontWeight:700, color:C.text, marginBottom:5 }}>Deep Code Review</div>
//                   <div style={{ fontSize:13, color:C.textMd }}>Analyse Big-O complexity, bugs & clean-code violations</div>
//                 </div>
//                 <Btn onClick={runReview} disabled={reviewing} variant="ghost">
//                   {reviewing ? <><span className="spin" />Analysing…</> : <>⌕ Run Analysis</>}
//                 </Btn>
//               </div>
//             ) : (
//               <div className="panel fu" style={{ padding:'1.5rem', borderTop:`2px solid ${C.violet}` }}>
//                 <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center',
//                   marginBottom:'1.2rem', paddingBottom:'1rem', borderBottom:`1px solid ${C.border}` }}>
//                   <div style={{ display:'flex', alignItems:'center', gap:10 }}>
//                     <div style={{ width:32, height:32, borderRadius:9, background:C.violetLo,
//                       border:`1px solid ${C.violetMd}`, display:'flex', alignItems:'center',
//                       justifyContent:'center', fontSize:15 }}>👨‍💻</div>
//                     <span style={{ fontSize:14, fontWeight:700, color:C.text }}>Senior Engineer Feedback</span>
//                   </div>
//                   <Btn onClick={runReview} disabled={reviewing} variant="ghost">↺ Re-analyse</Btn>
//                 </div>
//                 <div className="md"><ReactMarkdown>{cleanMd(review)}</ReactMarkdown></div>
//               </div>
//             )}

//             <Rule />

//             {/* ─── Architecture Diagram ─── */}
//             <div className="panel fu" style={{ padding:'1.5rem', borderTop:`2px solid ${C.amber}`, animationDelay:'.15s' }}>
//               <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center',
//                 marginBottom: diagramStr ? '1.2rem' : 0,
//                 paddingBottom: diagramStr ? '1rem' : 0,
//                 borderBottom: diagramStr ? `1px solid ${C.border}` : 'none',
//               }}>
//                 <div style={{ display:'flex', alignItems:'center', gap:10 }}>
//                   <div style={{ width:32, height:32, borderRadius:9, background:C.amberLo,
//                     border:`1px solid rgba(251,191,36,0.3)`, display:'flex',
//                     alignItems:'center', justifyContent:'center', fontSize:15 }}>🗺️</div>
//                   <div>
//                     <span style={{ fontSize:14, fontWeight:700, color:C.text, display:'block' }}>
//                       System Architecture
//                     </span>
//                     <span style={{ fontSize:12, color:C.textMd }}>AI-Generated Dependency Flow</span>
//                   </div>
//                 </div>
//                 <div style={{ display:'flex', gap:8 }}>
//                   {diagramStr && (
//                     <Btn onClick={() => setDiagramStr('')} variant="ghost">✕ Clear</Btn>
//                   )}
//                   <Btn onClick={generateDiagram} disabled={mapping} variant="ghost" accentColor={C.amber}>
//                     {mapping
//                       ? <><span className="spin" style={{ borderTopColor:C.amber, borderColor:C.amberLo }} />Mapping…</>
//                       : <>⌕ Map Architecture</>}
//                   </Btn>
//                 </div>
//               </div>

//               {mapping && (
//                 <div style={{ display:'flex', alignItems:'center', justifyContent:'center',
//                   gap:10, padding:'2rem 0' }}>
//                   <span className="spin" style={{ borderTopColor:C.amber, borderColor:C.amberLo,
//                     width:20, height:20 }} />
//                   <span style={{ fontSize:13, color:C.textMd }}>Compiling architecture map…</span>
//                 </div>
//               )}

//               {diagramStr && !mapping && (
//                 <div style={{ background:C.raised, borderRadius:12, padding:'1rem',
//                   border:`1px solid ${C.border}` }}>
//                   <ArchitectureDiagram chart={diagramStr} />
//                 </div>
//               )}

//               {diagramStr && !mapping && (
//                 <details style={{ marginTop:'0.8rem' }}>
//                   <summary style={{ fontSize:11.5, color:C.textLo, cursor:'pointer',
//                     letterSpacing:'.05em', userSelect:'none' }}>
//                     View raw Mermaid syntax
//                   </summary>
//                   <pre style={{ marginTop:8, padding:'0.85rem 1rem', background:C.bg,
//                     borderRadius:8, border:`1px solid ${C.border}`, fontSize:11,
//                     color:C.textMd, overflowX:'auto', lineHeight:1.7 }}>
//                     {diagramStr}
//                   </pre>
//                 </details>
//               )}
//             </div>

//             <Rule />

//             {/* ─── RAG Chat ─── */}
//             <div className="panel fu" style={{ padding:'1.5rem', borderTop:`2px solid ${C.emerald}`, animationDelay:'.16s' }}>
//               <div style={{ display:'flex', alignItems:'center', gap:10,
//                 paddingBottom:'1rem', marginBottom:'.8rem', borderBottom:`1px solid ${C.border}` }}>
//                 <div style={{ width:32, height:32, borderRadius:9, background:C.emeraldLo,
//                   border:`1px solid rgba(52,211,153,0.25)`, display:'flex',
//                   alignItems:'center', justifyContent:'center', fontSize:15 }}>🤖</div>
//                 <span style={{ fontSize:14, fontWeight:700, color:C.text }}>Ask the Codebase</span>
//                 <Badge color={C.emerald} bg={C.emeraldLo} border="rgba(52,211,153,0.28)">RAG</Badge>
//               </div>

//               <div className="chat-scroll" style={{ maxHeight:380, overflowY:'auto', display:'flex',
//                 flexDirection:'column', gap:'0.7rem', paddingRight:6, marginBottom:'1rem' }}>
//                 {chatHistory.length === 0 && !chatLoading && (
//                   <div style={{ textAlign:'center', color:C.textLo, fontSize:12.5, padding:'2rem 0' }}>
//                     Try: <em style={{ color:C.textMd }}>"How is the Dutch National Flag algorithm implemented?"</em>
//                   </div>
//                 )}
//                 {chatHistory.map((msg, i) => (
//                   <div key={i} className="fi"
//                     style={{ alignSelf:msg.role==='user'?'flex-end':'flex-start', maxWidth:'84%' }}>
//                     {msg.role === 'user' ? (
//                       <div style={{ background:`linear-gradient(135deg, ${C.violet}, #5b4de8)`,
//                         color:'#fff', padding:'10px 15px', borderRadius:'14px 14px 3px 14px',
//                         fontSize:13.5, lineHeight:1.6, boxShadow:'0 3px 12px rgba(139,127,255,0.28)' }}>
//                         {msg.content}
//                       </div>
//                     ) : (
//                       <div style={{ background:C.raised, border:`1px solid ${C.border}`,
//                         color:C.text, padding:'12px 16px', borderRadius:'3px 14px 14px 14px', fontSize:13.5 }}>
//                         <div className="md"><ReactMarkdown>{cleanMd(msg.content)}</ReactMarkdown></div>
//                       </div>
//                     )}
//                   </div>
//                 ))}
//                 {chatLoading && (
//                   <div style={{ alignSelf:'flex-start', background:C.raised, border:`1px solid ${C.border}`,
//                     padding:'11px 16px', borderRadius:'3px 14px 14px 14px',
//                     display:'flex', alignItems:'center', gap:9 }}>
//                     <span className="spin" style={{ borderTopColor:C.emerald, borderColor:C.emeraldLo }} />
//                     <span style={{ fontSize:12.5, color:C.textMd }}>Searching vector database…</span>
//                   </div>
//                 )}
//                 <div ref={chatEnd} />
//               </div>

//               <div style={{ display:'flex', gap:9 }}>
//                 <input value={question} onChange={e => setQuestion(e.target.value)}
//                   onKeyDown={e => e.key === 'Enter' && !chatLoading && askChat()}
//                   placeholder="Ask anything about this repository…"
//                   style={{ flex:1, background:C.bg, border:`1px solid ${C.border}`, borderRadius:10,
//                     padding:'11px 15px', fontSize:13.5, color:C.text, outline:'none',
//                     fontFamily:"'Outfit',sans-serif", transition:'border-color .2s' }}
//                   onFocus={e => e.target.style.borderColor = C.emerald}
//                   onBlur={e  => e.target.style.borderColor = C.border}
//                 />
//                 <Btn onClick={askChat} disabled={chatLoading || !question.trim()} accentColor={C.emerald}>
//                   Send ↗
//                 </Btn>
//               </div>
//             </div>

//           </div>
//         )}
//       </div>
//     </>
//   );
// }


// import { useState, useRef, useEffect } from 'react';
// import ReactMarkdown from 'react-markdown';

// const API = 'http://localhost:8080/api/repositories';

// /* ─── Markdown sanitizer ─────────────────────────────────────── */
// function cleanMd(text = '') {
//   return text
//     .replace(/\*{3,}([^*]+)\*{3,}/g, '**$1**')
//     .replace(/\*{4,}/g, '')
//     .replace(/^\* /gm, '- ')
//     .replace(/\n{3,}/g, '\n\n')
//     .trim();
// }

// /* ─── Design tokens ─────────────────────────────────────────── */
// const C = {
//   bg:       '#06070f',
//   surface:  '#0b0d1a',
//   raised:   '#0f1120',
//   border:   '#161929',
//   borderHi: '#202540',
//   violet:   '#8b7fff',
//   violetLo: 'rgba(139,127,255,0.10)',
//   violetMd: 'rgba(139,127,255,0.22)',
//   emerald:  '#34d399',
//   emeraldLo:'rgba(52,211,153,0.10)',
//   amber:    '#fbbf24',
//   amberLo:  'rgba(251,191,36,0.10)',
//   text:     '#eceef8',
//   textMd:   '#7c819c',
//   textLo:   '#2e334f',
// };

// const RANK_COLORS = [
//   { bg:'rgba(251,191,36,0.15)', border:'rgba(251,191,36,0.35)', text:'#fbbf24' },
//   { bg:'rgba(148,163,184,0.12)', border:'rgba(148,163,184,0.3)', text:'#94a3b8' },
//   { bg:'rgba(180,120,80,0.12)', border:'rgba(180,120,80,0.3)', text:'#c07050' },
// ];

// /* ─── Global styles ──────────────────────────────────────────── */
// const STYLES = `
//   @import url('https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700;800&family=Fira+Code:wght@400;500&display=swap');
//   *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
//   :root { color-scheme: dark; }
//   body { background: ${C.bg}; }

//   @keyframes fadeUp   { from{opacity:0;transform:translateY(12px)} to{opacity:1;transform:translateY(0)} }
//   @keyframes fadeIn   { from{opacity:0} to{opacity:1} }
//   @keyframes shimmer  { from{background-position:-600px 0} to{background-position:600px 0} }
//   @keyframes spin     { to{transform:rotate(360deg)} }
//   @keyframes floatA   { 0%,100%{transform:translate(0,0) scale(1)} 50%{transform:translate(10px,-8px) scale(1.05)} }
//   @keyframes floatB   { 0%,100%{transform:translate(0,0) scale(1)} 50%{transform:translate(-8px,10px) scale(1.04)} }
//   @keyframes barGrow  { from{width:0} to{width:var(--w)} }

//   .fu { animation: fadeUp .4s cubic-bezier(.22,1,.36,1) both; }
//   .fi { animation: fadeIn .3s ease both; }

//   .skeleton {
//     background: linear-gradient(90deg, ${C.border} 25%, #181d35 50%, ${C.border} 75%);
//     background-size: 600px 100%;
//     animation: shimmer 1.6s infinite;
//     border-radius: 14px;
//   }
//   .spin { width:15px;height:15px;border-radius:50%;border:2px solid ${C.violetMd};border-top-color:${C.violet};animation:spin .65s linear infinite;display:inline-block;flex-shrink:0; }

//   .chat-scroll::-webkit-scrollbar{width:3px}
//   .chat-scroll::-webkit-scrollbar-track{background:transparent}
//   .chat-scroll::-webkit-scrollbar-thumb{background:${C.border};border-radius:3px}

//   .md h1,.md h2,.md h3{color:${C.text};font-family:'Outfit',sans-serif;font-weight:700;margin:1.3em 0 .4em}
//   .md h1{font-size:17px}.md h2{font-size:15px}.md h3{font-size:13px}
//   .md p{color:${C.textMd};font-size:13.5px;line-height:1.85;margin:0 0 .85em}
//   .md code{background:${C.border};color:#a78bfa;padding:2px 6px;border-radius:5px;font-family:'Fira Code',monospace;font-size:11.5px}
//   .md pre{background:${C.surface};padding:.85rem 1rem;border-radius:10px;overflow-x:auto;border:1px solid ${C.border};margin:0 0 1em}
//   .md pre code{background:none;padding:0;color:${C.textMd}}
//   .md ul,.md ol{padding-left:1.35rem;color:${C.textMd};font-size:13.5px;margin:0 0 .85em}
//   .md li{margin-bottom:5px}
//   .md strong{color:#60a5fa}
//   .md em{color:${C.textMd};font-style:italic}
//   .md strong em,.md em strong{font-style:normal;color:#60a5fa}
//   .md blockquote{border-left:2px solid ${C.violet};padding:.4rem 1rem;color:${C.textLo};margin:0 0 1em}
//   .md ul ul,.md ul ol,.md ol ul,.md ol ol{margin:.3em 0 .3em;padding-left:1.2rem}
//   .md li>strong{color:#60a5fa}
//   .md li>em{color:${C.textMd}}

//   input::placeholder{color:${C.textLo}}
//   input{caret-color:${C.violet}}

//   .panel{background:${C.surface};border:1px solid ${C.border};border-radius:18px}
//   .panel:hover{border-color:${C.borderHi};transition:border-color .2s}

//   .contributor-row:hover{background:${C.raised}!important}
// `;

// /* ─── Commit heatmap ──────────────────────────────────────────── */
// function Heatmap({ dailyCounts }) {
//   const data = Object.entries(dailyCounts || {}).slice(-42);
//   const max  = Math.max(...data.map(([, v]) => v), 1);
//   const color = (n) => !n ? C.border : `rgba(139,127,255,${Math.max(0.14, n / max)})`;
//   const glow  = (n) => n > max * 0.65 ? `0 0 5px rgba(139,127,255,0.45)` : 'none';
//   return (
//     <div>
//       <div style={{ display:'flex', flexWrap:'wrap', gap:3 }}>
//         {data.length ? data.map(([date, count]) => (
//           <div key={date} title={`${date}: ${count} commit${count !== 1 ? 's' : ''}`}
//             style={{ width:13, height:13, borderRadius:3, cursor:'default', transition:'transform .12s, box-shadow .12s',
//               background: color(count), border:`1px solid rgba(139,127,255,${!count ? 0.07 : Math.min(count/max+0.1,0.75)})`,
//               boxShadow: glow(count),
//             }}
//             onMouseEnter={e=>{e.target.style.transform='scale(1.45)';e.target.style.zIndex=5}}
//             onMouseLeave={e=>{e.target.style.transform='scale(1)';e.target.style.zIndex=''}}
//           />
//         )) : <p style={{ fontSize:12, color:C.textLo, fontStyle:'italic' }}>No activity data.</p>}
//       </div>
//       <div style={{ display:'flex', alignItems:'center', gap:5, marginTop:9 }}>
//         <span style={{ fontSize:10.5, color:C.textLo }}>less</span>
//         {[0.07,0.18,0.38,0.62,1].map((o,i) => (
//           <div key={i} style={{ width:11, height:11, borderRadius:2, background:`rgba(139,127,255,${o})` }} />
//         ))}
//         <span style={{ fontSize:10.5, color:C.textLo }}>more</span>
//       </div>
//     </div>
//   );
// }

// /* ─── Contributors leaderboard ───────────────────────────────── */
// function Contributors({ list }) {
//   if (!list || !list.length) return null;
//   const maxC = list[0]?.commitCount || 1;
//   return (
//     <div style={{ display:'flex', flexDirection:'column', gap:6 }}>
//       {list.map((c, i) => {
//         const rank = RANK_COLORS[i] || { bg:C.violetLo, border:C.border, text:C.textMd };
//         const pct  = Math.round((c.commitCount / maxC) * 100);
//         const initial = (c.author || '?').charAt(0).toUpperCase();
//         return (
//           <div key={i} className="contributor-row fi" style={{
//             display:'flex', alignItems:'center', gap:12,
//             background:C.surface, border:`1px solid ${C.border}`, borderRadius:12,
//             padding:'9px 13px', transition:'background .15s', animationDelay:`${i*0.06}s`,
//           }}>
//             <div style={{ width:22, height:22, borderRadius:6, background:rank.bg, border:`1px solid ${rank.border}`,
//               display:'flex', alignItems:'center', justifyContent:'center',
//               fontSize:10, fontWeight:700, color:rank.text, flexShrink:0,
//             }}>{i + 1}</div>
//             <div style={{ width:30, height:30, borderRadius:'50%', flexShrink:0,
//               background:`linear-gradient(135deg, ${rank.text}22, ${rank.text}44)`,
//               border:`1px solid ${rank.border}`,
//               display:'flex', alignItems:'center', justifyContent:'center',
//               fontSize:12, fontWeight:700, color:rank.text,
//             }}>{initial}</div>
//             <div style={{ flex:1, minWidth:0 }}>
//               <div style={{ fontSize:13.5, fontWeight:600, color:C.text, marginBottom:4, overflow:'hidden', textOverflow:'ellipsis', whiteSpace:'nowrap' }}>
//                 {c.author}
//               </div>
//               <div style={{ height:3, borderRadius:99, background:C.border, overflow:'hidden' }}>
//                 <div style={{ '--w':`${pct}%`, width:`${pct}%`, height:'100%', borderRadius:99,
//                   background:`linear-gradient(90deg, ${rank.text}bb, ${rank.text})`,
//                   animation:'barGrow .6s cubic-bezier(.22,1,.36,1) both',
//                   animationDelay:`${0.1 + i * 0.06}s`,
//                 }} />
//               </div>
//             </div>
//             <div style={{ fontSize:12, fontWeight:700, color:rank.text, background:rank.bg,
//               border:`1px solid ${rank.border}`, borderRadius:8, padding:'3px 9px', flexShrink:0,
//             }}>{c.commitCount}</div>
//           </div>
//         );
//       })}
//     </div>
//   );
// }

// /* ─── Atoms ──────────────────────────────────────────────────── */
// const Badge = ({ children, color = C.violet, bg = C.violetLo, border = C.violetMd }) => (
//   <span style={{ fontSize:10, fontWeight:700, letterSpacing:'.1em', textTransform:'uppercase',
//     padding:'3px 9px', borderRadius:20, background:bg, color, border:`1px solid ${border}` }}>
//     {children}
//   </span>
// );

// const Rule = () => (
//   <div style={{ height:1, background:`linear-gradient(90deg,transparent,${C.border} 30%,${C.border} 70%,transparent)` }} />
// );

// const Label = ({ icon, children }) => (
//   <div style={{ display:'flex', alignItems:'center', gap:7, marginBottom:'1rem' }}>
//     <span style={{ fontSize:13 }}>{icon}</span>
//     <span style={{ fontSize:10.5, fontWeight:700, color:C.textMd, letterSpacing:'.1em', textTransform:'uppercase' }}>{children}</span>
//   </div>
// );

// /* ─── Button ──────────────────────────────────────────────────── */
// function Btn({ onClick, disabled, children, variant='primary', accentColor }) {
//   const vc = accentColor || C.violet;
//   const styles = {
//     primary: {
//       background: disabled ? C.violetLo : `linear-gradient(135deg, ${vc} 0%, #6857e8 100%)`,
//       border:'none', color:'#fff',
//       boxShadow: disabled ? 'none' : `0 2px 12px rgba(139,127,255,0.3)`,
//     },
//     ghost: {
//       background:'transparent', border:`1px solid ${C.borderHi}`, color:C.textMd,
//     },
//   };
//   return (
//     <button onClick={onClick} disabled={disabled}
//       style={{ display:'inline-flex', alignItems:'center', gap:7, padding:'9px 19px',
//         fontSize:12.5, fontWeight:600, borderRadius:10, cursor:disabled?'not-allowed':'pointer',
//         transition:'all .16s', whiteSpace:'nowrap', opacity:disabled?.55:1,
//         fontFamily:"'Outfit',sans-serif", letterSpacing:'.02em',
//         ...styles[variant],
//       }}
//       onMouseEnter={e => { if(!disabled){ e.currentTarget.style.transform='translateY(-1px)'; if(variant==='primary') e.currentTarget.style.boxShadow=`0 4px 18px rgba(139,127,255,0.45)`; } }}
//       onMouseLeave={e => { e.currentTarget.style.transform=''; if(variant==='primary') e.currentTarget.style.boxShadow=styles.primary.boxShadow||'none'; }}
//     >{children}</button>
//   );
// }

// /* ─── Health Score Widget ────────────────────────────────────── */
// function HealthScore({ score }) {
//   if (score == null) return null;

//   let color = C.emerald, bg = C.emeraldLo, text = 'Healthy';
//   if (score < 50) {
//     color = '#ef4444';
//     bg = 'rgba(239,68,68,0.1)';
//     text = 'At Risk';
//   } else if (score < 80) {
//     color = C.amber;
//     bg = C.amberLo;
//     text = 'Needs Work';
//   }

//   return (
//     <div className="panel" style={{ padding:'1.3rem 1.8rem', display:'flex', flexDirection:'column',
//       alignItems:'center', justifyContent:'center', gap:5, minWidth:140 }}>
//       <div style={{ fontSize:10, color:C.textLo, letterSpacing:'.12em', textTransform:'uppercase' }}>Health Score</div>
//       <div style={{ display:'flex', alignItems:'baseline', gap:2 }}>
//         <span style={{ fontSize:42, fontWeight:800, color:color, fontFamily:"'Fira Code',monospace", lineHeight:1, textShadow:`0 0 15px ${color}40` }}>
//           {score}
//         </span>
//       </div>
//       <Badge color={color} bg={bg} border={color}>{text}</Badge>
//     </div>
//   );
// }

// /* ─── Main App ────────────────────────────────────────────────── */
// export default function App() {
//   const [url,          setUrl]         = useState('https://github.com/Nagendrasriram/Practice-for-Dsa-using-Java-Arrays');
//   const [data,         setData]        = useState(null);
//   const [contributors, setContributors]= useState([]);
//   const [review,       setReview]      = useState('');
//   const [loading,      setLoading]     = useState(false);
//   const [loadingMsg,   setLoadingMsg]  = useState('');
//   const [reviewing,    setReviewing]   = useState(false);
//   const [error,        setError]       = useState('');
//   const [question,     setQuestion]    = useState('');
//   const [chatHistory,  setChatHistory] = useState([]);
//   const [chatLoading,  setChatLoading] = useState(false);
//   const chatEnd = useRef(null);

//   useEffect(() => { chatEnd.current?.scrollIntoView({ behavior:'smooth' }); }, [chatHistory, chatLoading]);

//   const enc = () => encodeURIComponent(url);

//   // =========================================================================
//   // ANALYZE — Step 1: clone+index, Step 2: fetch summary, Step 3: contributors
//   // =========================================================================
//   async function analyze() {
//     setLoading(true);
//     setError('');
//     setData(null);
//     setReview('');
//     setChatHistory([]);
//     setContributors([]);

//     try {
//       // ── Step 1: Clone + Index + Save ──────────────────────────────────
//       setLoadingMsg('Cloning and indexing repository…');
//       const analyzeRes = await fetch(`${API}/analyze?url=${enc()}`);

//       if (!analyzeRes.ok) {
//         let errMsg = `HTTP ${analyzeRes.status}`;
//         try {
//           const errJson = await analyzeRes.json();
//           errMsg = errJson.error || errMsg;
//         } catch (_) {
//           errMsg = await analyzeRes.text().catch(() => errMsg);
//         }
//         throw new Error(errMsg);
//       }

//       await analyzeRes.json();

//       // ── Step 2: Fetch Dashboard Summary ───────────────────────────────
//       setLoadingMsg('Generating AI summary…');
//       const summaryRes = await fetch(`${API}/summary?url=${enc()}`);

//       if (!summaryRes.ok) {
//         let errMsg = `Summary fetch failed — HTTP ${summaryRes.status}`;
//         try {
//           const errJson = await summaryRes.json();
//           errMsg = errJson.error || errMsg;
//         } catch (_) {
//           errMsg = await summaryRes.text().catch(() => errMsg);
//         }
//         throw new Error(errMsg);
//       }

//       const summaryData = await summaryRes.json();
//       setData(summaryData);

//       // ── Step 3: Contributors — non-fatal, won't crash if it fails ─────
//       setLoadingMsg('Loading contributors…');
//       try {
//         const cRes = await fetch(`http://localhost:8080/api/contributors?url=${enc()}`);
//         if (cRes.ok) {
//           const cData = await cRes.json();
//           setContributors(Array.isArray(cData) ? cData : []);
//         }
//       } catch (_) {
//         // Contributors are optional — silently ignore
//       }

//     } catch (err) {
//       setError(`Analysis failed — ${err.message || 'Is your Spring Boot server running on :8080?'}`);
//     } finally {
//       setLoading(false);
//       setLoadingMsg('');
//     }
//   }

//   // =========================================================================
//   // CODE REVIEW
//   // =========================================================================
//   async function runReview() {
//     setReviewing(true);
//     setReview('');
//     try {
//       const res = await fetch(`${API}/code-review?url=${enc()}`);
//       if (!res.ok) {
//         const msg = await res.text().catch(() => '');
//         throw new Error(msg || `HTTP ${res.status}`);
//       }
//       setReview(await res.text());
//     } catch (err) {
//       setError(`Code review failed — ${err.message || 'Check IntelliJ console.'}`);
//     } finally {
//       setReviewing(false);
//     }
//   }

//   // =========================================================================
//   // RAG CHAT
//   // =========================================================================
//   async function askChat() {
//     if (!question.trim()) return;
//     const userQ = question;
//     setQuestion('');
//     setChatHistory(prev => [...prev, { role:'user', content:userQ }]);
//     setChatLoading(true);
//     try {
//       const res = await fetch('http://localhost:8080/api/chat', {
//         method: 'POST',
//         headers: { 'Content-Type': 'application/json' },
//         body: JSON.stringify({ question: userQ, url }),
//       });
//       if (!res.ok) {
//         const msg = await res.text().catch(() => '');
//         throw new Error(msg || `HTTP ${res.status}`);
//       }
//       const answer = await res.text();
//       setChatHistory(prev => [...prev, { role:'ai', content:answer }]);
//     } catch (err) {
//       setChatHistory(prev => [...prev, {
//         role:'ai',
//         content:`⚠ Chat failed: ${err.message || 'Ensure backend is running.'}`,
//       }]);
//     } finally {
//       setChatLoading(false);
//     }
//   }

//   return (
//     <>
//       <style>{STYLES}</style>

//       {/* Ambient background orbs */}
//       <div style={{ position:'fixed', inset:0, pointerEvents:'none', zIndex:0, overflow:'hidden' }}>
//         <div style={{ position:'absolute', top:'-10%', right:'10%', width:480, height:480, borderRadius:'50%',
//           background:'radial-gradient(circle, rgba(139,127,255,0.065) 0%, transparent 68%)',
//           animation:'floatA 10s ease-in-out infinite' }} />
//         <div style={{ position:'absolute', bottom:'-5%', left:'-8%', width:360, height:360, borderRadius:'50%',
//           background:'radial-gradient(circle, rgba(52,211,153,0.045) 0%, transparent 68%)',
//           animation:'floatB 13s ease-in-out infinite' }} />
//         <div style={{ position:'absolute', top:'40%', left:'40%', width:260, height:260, borderRadius:'50%',
//           background:'radial-gradient(circle, rgba(251,191,36,0.025) 0%, transparent 65%)',
//           animation:'floatA 17s ease-in-out infinite reverse' }} />
//       </div>

//       <div style={{ minHeight:'100vh', padding:'3rem 1.5rem 8rem', position:'relative', zIndex:1,
//         fontFamily:"'Outfit', sans-serif", maxWidth:920, margin:'0 auto' }}>

//         {/* ─── Header ─── */}
//         <header style={{ marginBottom:'2.8rem' }} className="fu">
//           <div style={{ display:'flex', alignItems:'center', gap:12, marginBottom:10 }}>
//             <div style={{ width:40, height:40, borderRadius:12, flexShrink:0,
//               background:`linear-gradient(145deg, ${C.violet}, #5b4de8)`,
//               display:'flex', alignItems:'center', justifyContent:'center',
//               boxShadow:'0 4px 18px rgba(139,127,255,0.38)',
//             }}>
//               <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
//                 <circle cx="10" cy="10" r="3.8" fill="white" fillOpacity=".92"/>
//                 <circle cx="10" cy="10" r="7.5" stroke="white" strokeOpacity=".35" strokeWidth="1.1"/>
//                 <circle cx="10" cy="10" r="4.8" stroke="white" strokeOpacity=".18" strokeWidth="3"/>
//                 {[[10,1.5,10,4],[10,16,10,18.5],[1.5,10,4,10],[16,10,18.5,10]].map(([x1,y1,x2,y2],i) => (
//                   <line key={i} x1={x1} y1={y1} x2={x2} y2={y2} stroke="white" strokeOpacity=".55" strokeWidth="1.1" strokeLinecap="round"/>
//                 ))}
//               </svg>
//             </div>
//             <h1 style={{ fontSize:28, fontWeight:800, color:C.text, letterSpacing:'-.035em' }}>GitInsight</h1>
//             <Badge>Beta</Badge>
//           </div>
//           <p style={{ fontSize:13.5, color:C.textMd, paddingLeft:52, letterSpacing:'.01em' }}>
//             AI-powered codebase health · commit analytics · static review
//           </p>
//         </header>

//         {/* ─── URL Input ─── */}
//         <div className="panel fu" style={{ padding:'1.3rem 1.5rem', marginBottom:'1rem', animationDelay:'.04s' }}>
//           <div style={{ fontSize:10, color:C.textLo, letterSpacing:'.12em', textTransform:'uppercase', marginBottom:9 }}>
//             Repository URL
//           </div>
//           <div style={{ display:'flex', gap:10 }}>
//             <div style={{ flex:1, position:'relative' }}>
//               <span style={{ position:'absolute', left:12, top:'50%', transform:'translateY(-50%)', color:C.textLo, fontSize:14, pointerEvents:'none' }}>⌥</span>
//               <input value={url} onChange={e => setUrl(e.target.value)}
//                 onKeyDown={e => e.key === 'Enter' && analyze()}
//                 placeholder="https://github.com/user/repo"
//                 style={{ width:'100%', background:C.bg, border:`1px solid ${C.border}`, borderRadius:10,
//                   padding:'10px 14px 10px 30px', fontSize:12.5, color:C.text, outline:'none',
//                   fontFamily:"'Fira Code', monospace", transition:'border-color .2s',
//                 }}
//                 onFocus={e => e.target.style.borderColor = C.violet}
//                 onBlur={e  => e.target.style.borderColor = C.border}
//               />
//             </div>
//             <Btn onClick={analyze} disabled={loading}>
//               {loading ? <><span className="spin" />{loadingMsg || 'Analyzing…'}</> : <>✦ Generate Insight</>}
//             </Btn>
//           </div>
//         </div>

//         {/* ─── Error ─── */}
//         {error && (
//           <div className="fi" style={{ background:'rgba(239,68,68,0.07)', border:'1px solid rgba(239,68,68,0.22)',
//             borderRadius:12, padding:'11px 16px', fontSize:13, color:'#fca5a5',
//             marginBottom:'1rem', display:'flex', alignItems:'center', gap:8 }}>
//             <span>⚠</span>{error}
//           </div>
//         )}

//         {/* ─── Loading skeletons ─── */}
//         {loading && (
//           <div style={{ display:'flex', flexDirection:'column', gap:'1rem' }} className="fi">
//             <div style={{ fontSize:12.5, color:C.textMd, textAlign:'center', marginBottom:4, letterSpacing:'.04em' }}>
//               {loadingMsg}
//             </div>
//             {[180, 140, 200].map((h, i) => (
//               <div key={i} className="skeleton" style={{ height:h, animationDelay:`${i*0.1}s` }} />
//             ))}
//           </div>
//         )}

//         {/* ─── Dashboard ─── */}
//         {data && !loading && (
//           <div style={{ display:'flex', flexDirection:'column', gap:'1rem' }}>

//             {/* Row 1: repo name + commit count + HEALTH SCORE */}
//             <div style={{ display:'grid', gridTemplateColumns:'1fr auto auto', gap:'1rem',
//               animation:'fadeUp .4s cubic-bezier(.22,1,.36,1) both', animationDelay:'.05s' }}>

//               <div className="panel" style={{ padding:'1.3rem 1.6rem' }}>
//                 <div style={{ fontSize:10, color:C.textLo, letterSpacing:'.12em', textTransform:'uppercase', marginBottom:7 }}>Repository</div>
//                 <div style={{ fontSize:20, fontWeight:700, color:C.text, fontFamily:"'Fira Code',monospace",
//                   overflow:'hidden', textOverflow:'ellipsis', whiteSpace:'nowrap' }}>
//                   {data.repositoryName || url.split('/').pop()}
//                 </div>
//                 <div style={{ fontSize:11.5, color:C.textLo, marginTop:5, fontFamily:"'Fira Code',monospace" }}>
//                   {url.replace('https://github.com/', '')}
//                 </div>
//               </div>

//               <div className="panel" style={{ padding:'1.3rem 1.8rem', display:'flex', flexDirection:'column',
//                 alignItems:'center', justifyContent:'center', gap:5, minWidth:140 }}>
//                 <div style={{ fontSize:10, color:C.textLo, letterSpacing:'.12em', textTransform:'uppercase' }}>Total Commits</div>
//                 <div style={{ fontSize:42, fontWeight:800, color:C.text, fontFamily:"'Fira Code',monospace", lineHeight:1 }}>
//                   {data.totalCommits ?? '—'}
//                 </div>
//               </div>

//               {/* NEW: Health Score Widget */}
//               <HealthScore score={data.healthScore} />

//             </div>

//             {/* Row 2: AI summary + activity heatmap */}
//             <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr', gap:'1rem',
//               animation:'fadeUp .4s cubic-bezier(.22,1,.36,1) both', animationDelay:'.09s' }}>
//               <div className="panel" style={{ padding:'1.4rem 1.5rem', borderLeft:`2px solid ${C.violet}` }}>
//                 <Label icon="✦">AI Summary</Label>
//                 <p style={{ fontSize:13.5, color:C.textMd, lineHeight:1.85, margin:0 }}>
//                   {data.aiSummary || 'No summary available.'}
//                 </p>
//               </div>

//               <div style={{ display:'flex', flexDirection:'column', gap:'1rem' }}>
//                 <div className="panel" style={{ padding:'1.4rem 1.5rem' }}>
//                   <Label icon="◫">30-Day Activity</Label>
//                   <Heatmap dailyCounts={data.dailyCommitCounts} />
//                 </div>

//                 {contributors.length > 0 && (
//                   <div className="panel fu" style={{ padding:'1.4rem 1.5rem', animationDelay:'.14s' }}>
//                     <Label icon="★">Top Contributors</Label>
//                     <Contributors list={contributors} />
//                   </div>
//                 )}
//               </div>
//             </div>

//             <Rule />

//             {/* ─── Code Review ─── */}
//             {!review ? (
//               <div className="panel fu" style={{ display:'flex', justifyContent:'space-between', alignItems:'center',
//                 padding:'1.25rem 1.6rem', animationDelay:'.13s' }}>
//                 <div>
//                   <div style={{ fontSize:14.5, fontWeight:700, color:C.text, marginBottom:5 }}>Deep Code Review</div>
//                   <div style={{ fontSize:13, color:C.textMd }}>Analyse Big-O complexity, bugs & clean-code violations</div>
//                 </div>
//                 <Btn onClick={runReview} disabled={reviewing} variant="ghost">
//                   {reviewing ? <><span className="spin" />Analysing…</> : <>⌕ Run Analysis</>}
//                 </Btn>
//               </div>
//             ) : (
//               <div className="panel fu" style={{ padding:'1.5rem', borderTop:`2px solid ${C.violet}` }}>
//                 <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center',
//                   marginBottom:'1.2rem', paddingBottom:'1rem', borderBottom:`1px solid ${C.border}` }}>
//                   <div style={{ display:'flex', alignItems:'center', gap:10 }}>
//                     <div style={{ width:32, height:32, borderRadius:9, background:C.violetLo, border:`1px solid ${C.violetMd}`,
//                       display:'flex', alignItems:'center', justifyContent:'center', fontSize:15 }}>👨‍💻</div>
//                     <span style={{ fontSize:14, fontWeight:700, color:C.text }}>Senior Engineer Feedback</span>
//                   </div>
//                   <Btn onClick={runReview} disabled={reviewing} variant="ghost">↺ Re-analyse</Btn>
//                 </div>
//                 <div className="md"><ReactMarkdown>{cleanMd(review)}</ReactMarkdown></div>
//               </div>
//             )}

//             <Rule />

//             {/* ─── RAG Chat ─── */}
//             <div className="panel fu" style={{ padding:'1.5rem', borderTop:`2px solid ${C.emerald}`, animationDelay:'.16s' }}>
//               <div style={{ display:'flex', alignItems:'center', gap:10,
//                 paddingBottom:'1rem', marginBottom:'.8rem', borderBottom:`1px solid ${C.border}` }}>
//                 <div style={{ width:32, height:32, borderRadius:9, background:C.emeraldLo,
//                   border:`1px solid rgba(52,211,153,0.25)`,
//                   display:'flex', alignItems:'center', justifyContent:'center', fontSize:15 }}>🤖</div>
//                 <span style={{ fontSize:14, fontWeight:700, color:C.text }}>Ask the Codebase</span>
//                 <Badge color={C.emerald} bg={C.emeraldLo} border="rgba(52,211,153,0.28)">RAG</Badge>
//               </div>

//               <div className="chat-scroll" style={{ maxHeight:380, overflowY:'auto', display:'flex',
//                 flexDirection:'column', gap:'0.7rem', paddingRight:6, marginBottom:'1rem' }}>
//                 {chatHistory.length === 0 && !chatLoading && (
//                   <div style={{ textAlign:'center', color:C.textLo, fontSize:12.5, padding:'2rem 0' }}>
//                     Try: <em style={{ color:C.textMd }}>"How is the Dutch National Flag algorithm implemented?"</em>
//                   </div>
//                 )}
//                 {chatHistory.map((msg, i) => (
//                   <div key={i} className="fi" style={{ alignSelf:msg.role==='user'?'flex-end':'flex-start', maxWidth:'84%' }}>
//                     {msg.role === 'user' ? (
//                       <div style={{ background:`linear-gradient(135deg, ${C.violet}, #5b4de8)`,
//                         color:'#fff', padding:'10px 15px', borderRadius:'14px 14px 3px 14px',
//                         fontSize:13.5, lineHeight:1.6, boxShadow:'0 3px 12px rgba(139,127,255,0.28)' }}>
//                         {msg.content}
//                       </div>
//                     ) : (
//                       <div style={{ background:C.raised, border:`1px solid ${C.border}`,
//                         color:C.text, padding:'12px 16px', borderRadius:'3px 14px 14px 14px', fontSize:13.5 }}>
//                         <div className="md"><ReactMarkdown>{cleanMd(msg.content)}</ReactMarkdown></div>
//                       </div>
//                     )}
//                   </div>
//                 ))}
//                 {chatLoading && (
//                   <div style={{ alignSelf:'flex-start', background:C.raised, border:`1px solid ${C.border}`,
//                     padding:'11px 16px', borderRadius:'3px 14px 14px 14px',
//                     display:'flex', alignItems:'center', gap:9 }}>
//                     <span className="spin" style={{ borderTopColor:C.emerald, borderColor:C.emeraldLo }} />
//                     <span style={{ fontSize:12.5, color:C.textMd }}>Searching vector database…</span>
//                   </div>
//                 )}
//                 <div ref={chatEnd} />
//               </div>

//               <div style={{ display:'flex', gap:9 }}>
//                 <input value={question} onChange={e => setQuestion(e.target.value)}
//                   onKeyDown={e => e.key === 'Enter' && !chatLoading && askChat()}
//                   placeholder="Ask anything about this repository…"
//                   style={{ flex:1, background:C.bg, border:`1px solid ${C.border}`, borderRadius:10,
//                     padding:'11px 15px', fontSize:13.5, color:C.text, outline:'none',
//                     fontFamily:"'Outfit',sans-serif", transition:'border-color .2s' }}
//                   onFocus={e => e.target.style.borderColor = C.emerald}
//                   onBlur={e  => e.target.style.borderColor = C.border}
//                 />
//                 <Btn onClick={askChat} disabled={chatLoading || !question.trim()} accentColor={C.emerald}>
//                   Send ↗
//                 </Btn>
//               </div>
//             </div>

//           </div>
//         )}
//       </div>
//     </>
//   );
// }

// import { useState, useRef, useEffect } from 'react';
// import ReactMarkdown from 'react-markdown';

// const API = 'http://localhost:8080/api/repositories';

// /* ─── Markdown sanitizer ─────────────────────────────────────── */
// function cleanMd(text = '') {
//   return text
//     .replace(/\*{3,}([^*]+)\*{3,}/g, '**$1**')
//     .replace(/\*{4,}/g, '')
//     .replace(/^\* /gm, '- ')
//     .replace(/\n{3,}/g, '\n\n')
//     .trim();
// }

// /* ─── Design tokens ─────────────────────────────────────────── */
// const C = {
//   bg:       '#06070f',
//   surface:  '#0b0d1a',
//   raised:   '#0f1120',
//   border:   '#161929',
//   borderHi: '#202540',
//   violet:   '#8b7fff',
//   violetLo: 'rgba(139,127,255,0.10)',
//   violetMd: 'rgba(139,127,255,0.22)',
//   emerald:  '#34d399',
//   emeraldLo:'rgba(52,211,153,0.10)',
//   amber:    '#fbbf24',
//   amberLo:  'rgba(251,191,36,0.10)',
//   text:     '#eceef8',
//   textMd:   '#7c819c',
//   textLo:   '#2e334f',
// };

// const RANK_COLORS = [
//   { bg:'rgba(251,191,36,0.15)', border:'rgba(251,191,36,0.35)', text:'#fbbf24' },
//   { bg:'rgba(148,163,184,0.12)', border:'rgba(148,163,184,0.3)', text:'#94a3b8' },
//   { bg:'rgba(180,120,80,0.12)', border:'rgba(180,120,80,0.3)', text:'#c07050' },
// ];

// /* ─── Global styles ──────────────────────────────────────────── */
// const STYLES = `
//   @import url('https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700;800&family=Fira+Code:wght@400;500&display=swap');
//   *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
//   :root { color-scheme: dark; }
//   body { background: ${C.bg}; }

//   @keyframes fadeUp   { from{opacity:0;transform:translateY(12px)} to{opacity:1;transform:translateY(0)} }
//   @keyframes fadeIn   { from{opacity:0} to{opacity:1} }
//   @keyframes shimmer  { from{background-position:-600px 0} to{background-position:600px 0} }
//   @keyframes spin     { to{transform:rotate(360deg)} }
//   @keyframes floatA   { 0%,100%{transform:translate(0,0) scale(1)} 50%{transform:translate(10px,-8px) scale(1.05)} }
//   @keyframes floatB   { 0%,100%{transform:translate(0,0) scale(1)} 50%{transform:translate(-8px,10px) scale(1.04)} }
//   @keyframes barGrow  { from{width:0} to{width:var(--w)} }

//   .fu { animation: fadeUp .4s cubic-bezier(.22,1,.36,1) both; }
//   .fi { animation: fadeIn .3s ease both; }

//   .skeleton {
//     background: linear-gradient(90deg, ${C.border} 25%, #181d35 50%, ${C.border} 75%);
//     background-size: 600px 100%;
//     animation: shimmer 1.6s infinite;
//     border-radius: 14px;
//   }
//   .spin { width:15px;height:15px;border-radius:50%;border:2px solid ${C.violetMd};border-top-color:${C.violet};animation:spin .65s linear infinite;display:inline-block;flex-shrink:0; }

//   .chat-scroll::-webkit-scrollbar{width:3px}
//   .chat-scroll::-webkit-scrollbar-track{background:transparent}
//   .chat-scroll::-webkit-scrollbar-thumb{background:${C.border};border-radius:3px}

//   .md h1,.md h2,.md h3{color:${C.text};font-family:'Outfit',sans-serif;font-weight:700;margin:1.3em 0 .4em}
//   .md h1{font-size:17px}.md h2{font-size:15px}.md h3{font-size:13px}
//   .md p{color:${C.textMd};font-size:13.5px;line-height:1.85;margin:0 0 .85em}
//   .md code{background:${C.border};color:#a78bfa;padding:2px 6px;border-radius:5px;font-family:'Fira Code',monospace;font-size:11.5px}
//   .md pre{background:${C.surface};padding:.85rem 1rem;border-radius:10px;overflow-x:auto;border:1px solid ${C.border};margin:0 0 1em}
//   .md pre code{background:none;padding:0;color:${C.textMd}}
//   .md ul,.md ol{padding-left:1.35rem;color:${C.textMd};font-size:13.5px;margin:0 0 .85em}
//   .md li{margin-bottom:5px}
//   .md strong{color:#60a5fa}
//   .md em{color:${C.textMd};font-style:italic}
//   .md strong em,.md em strong{font-style:normal;color:#60a5fa}
//   .md blockquote{border-left:2px solid ${C.violet};padding:.4rem 1rem;color:${C.textLo};margin:0 0 1em}
//   .md ul ul,.md ul ol,.md ol ul,.md ol ol{margin:.3em 0 .3em;padding-left:1.2rem}
//   .md li>strong{color:#60a5fa}
//   .md li>em{color:${C.textMd}}

//   input::placeholder{color:${C.textLo}}
//   input{caret-color:${C.violet}}

//   .panel{background:${C.surface};border:1px solid ${C.border};border-radius:18px}
//   .panel:hover{border-color:${C.borderHi};transition:border-color .2s}

//   .contributor-row:hover{background:${C.raised}!important}
// `;

// /* ─── Commit heatmap ──────────────────────────────────────────── */
// function Heatmap({ dailyCounts }) {
//   const data = Object.entries(dailyCounts || {}).slice(-42);
//   const max  = Math.max(...data.map(([, v]) => v), 1);
//   const color = (n) => !n ? C.border : `rgba(139,127,255,${Math.max(0.14, n / max)})`;
//   const glow  = (n) => n > max * 0.65 ? `0 0 5px rgba(139,127,255,0.45)` : 'none';
//   return (
//     <div>
//       <div style={{ display:'flex', flexWrap:'wrap', gap:3 }}>
//         {data.length ? data.map(([date, count]) => (
//           <div key={date} title={`${date}: ${count} commit${count !== 1 ? 's' : ''}`}
//             style={{ width:13, height:13, borderRadius:3, cursor:'default', transition:'transform .12s, box-shadow .12s',
//               background: color(count), border:`1px solid rgba(139,127,255,${!count ? 0.07 : Math.min(count/max+0.1,0.75)})`,
//               boxShadow: glow(count),
//             }}
//             onMouseEnter={e=>{e.target.style.transform='scale(1.45)';e.target.style.zIndex=5}}
//             onMouseLeave={e=>{e.target.style.transform='scale(1)';e.target.style.zIndex=''}}
//           />
//         )) : <p style={{ fontSize:12, color:C.textLo, fontStyle:'italic' }}>No activity data.</p>}
//       </div>
//       <div style={{ display:'flex', alignItems:'center', gap:5, marginTop:9 }}>
//         <span style={{ fontSize:10.5, color:C.textLo }}>less</span>
//         {[0.07,0.18,0.38,0.62,1].map((o,i) => (
//           <div key={i} style={{ width:11, height:11, borderRadius:2, background:`rgba(139,127,255,${o})` }} />
//         ))}
//         <span style={{ fontSize:10.5, color:C.textLo }}>more</span>
//       </div>
//     </div>
//   );
// }

// /* ─── Contributors leaderboard ───────────────────────────────── */
// function Contributors({ list }) {
//   if (!list || !list.length) return null;
//   const maxC = list[0]?.commitCount || 1;
//   return (
//     <div style={{ display:'flex', flexDirection:'column', gap:6 }}>
//       {list.map((c, i) => {
//         const rank = RANK_COLORS[i] || { bg:C.violetLo, border:C.border, text:C.textMd };
//         const pct  = Math.round((c.commitCount / maxC) * 100);
//         const initial = (c.author || '?').charAt(0).toUpperCase();
//         return (
//           <div key={i} className="contributor-row fi" style={{
//             display:'flex', alignItems:'center', gap:12,
//             background:C.surface, border:`1px solid ${C.border}`, borderRadius:12,
//             padding:'9px 13px', transition:'background .15s', animationDelay:`${i*0.06}s`,
//           }}>
//             <div style={{ width:22, height:22, borderRadius:6, background:rank.bg, border:`1px solid ${rank.border}`,
//               display:'flex', alignItems:'center', justifyContent:'center',
//               fontSize:10, fontWeight:700, color:rank.text, flexShrink:0,
//             }}>{i + 1}</div>
//             <div style={{ width:30, height:30, borderRadius:'50%', flexShrink:0,
//               background:`linear-gradient(135deg, ${rank.text}22, ${rank.text}44)`,
//               border:`1px solid ${rank.border}`,
//               display:'flex', alignItems:'center', justifyContent:'center',
//               fontSize:12, fontWeight:700, color:rank.text,
//             }}>{initial}</div>
//             <div style={{ flex:1, minWidth:0 }}>
//               <div style={{ fontSize:13.5, fontWeight:600, color:C.text, marginBottom:4, overflow:'hidden', textOverflow:'ellipsis', whiteSpace:'nowrap' }}>
//                 {c.author}
//               </div>
//               <div style={{ height:3, borderRadius:99, background:C.border, overflow:'hidden' }}>
//                 <div style={{ '--w':`${pct}%`, width:`${pct}%`, height:'100%', borderRadius:99,
//                   background:`linear-gradient(90deg, ${rank.text}bb, ${rank.text})`,
//                   animation:'barGrow .6s cubic-bezier(.22,1,.36,1) both',
//                   animationDelay:`${0.1 + i * 0.06}s`,
//                 }} />
//               </div>
//             </div>
//             <div style={{ fontSize:12, fontWeight:700, color:rank.text, background:rank.bg,
//               border:`1px solid ${rank.border}`, borderRadius:8, padding:'3px 9px', flexShrink:0,
//             }}>{c.commitCount}</div>
//           </div>
//         );
//       })}
//     </div>
//   );
// }

// /* ─── Atoms ──────────────────────────────────────────────────── */
// const Badge = ({ children, color = C.violet, bg = C.violetLo, border = C.violetMd }) => (
//   <span style={{ fontSize:10, fontWeight:700, letterSpacing:'.1em', textTransform:'uppercase',
//     padding:'3px 9px', borderRadius:20, background:bg, color, border:`1px solid ${border}` }}>
//     {children}
//   </span>
// );

// const Rule = () => (
//   <div style={{ height:1, background:`linear-gradient(90deg,transparent,${C.border} 30%,${C.border} 70%,transparent)` }} />
// );

// const Label = ({ icon, children }) => (
//   <div style={{ display:'flex', alignItems:'center', gap:7, marginBottom:'1rem' }}>
//     <span style={{ fontSize:13 }}>{icon}</span>
//     <span style={{ fontSize:10.5, fontWeight:700, color:C.textMd, letterSpacing:'.1em', textTransform:'uppercase' }}>{children}</span>
//   </div>
// );

// /* ─── Button ──────────────────────────────────────────────────── */
// function Btn({ onClick, disabled, children, variant='primary', accentColor }) {
//   const vc = accentColor || C.violet;
//   const styles = {
//     primary: {
//       background: disabled ? C.violetLo : `linear-gradient(135deg, ${vc} 0%, #6857e8 100%)`,
//       border:'none', color:'#fff',
//       boxShadow: disabled ? 'none' : `0 2px 12px rgba(139,127,255,0.3)`,
//     },
//     ghost: {
//       background:'transparent', border:`1px solid ${C.borderHi}`, color:C.textMd,
//     },
//   };
//   return (
//     <button onClick={onClick} disabled={disabled}
//       style={{ display:'inline-flex', alignItems:'center', gap:7, padding:'9px 19px',
//         fontSize:12.5, fontWeight:600, borderRadius:10, cursor:disabled?'not-allowed':'pointer',
//         transition:'all .16s', whiteSpace:'nowrap', opacity:disabled?.55:1,
//         fontFamily:"'Outfit',sans-serif", letterSpacing:'.02em',
//         ...styles[variant],
//       }}
//       onMouseEnter={e => { if(!disabled){ e.currentTarget.style.transform='translateY(-1px)'; if(variant==='primary') e.currentTarget.style.boxShadow=`0 4px 18px rgba(139,127,255,0.45)`; } }}
//       onMouseLeave={e => { e.currentTarget.style.transform=''; if(variant==='primary') e.currentTarget.style.boxShadow=styles.primary.boxShadow||'none'; }}
//     >{children}</button>
//   );
// }

// /* ─── Main App ────────────────────────────────────────────────── */
// export default function App() {
//   const [url,          setUrl]         = useState('https://github.com/Nagendrasriram/Practice-for-Dsa-using-Java-Arrays');
//   const [data,         setData]        = useState(null);
//   const [contributors, setContributors]= useState([]);
//   const [review,       setReview]      = useState('');
//   const [loading,      setLoading]     = useState(false);
//   const [loadingMsg,   setLoadingMsg]  = useState('');
//   const [reviewing,    setReviewing]   = useState(false);
//   const [error,        setError]       = useState('');
//   const [question,     setQuestion]    = useState('');
//   const [chatHistory,  setChatHistory] = useState([]);
//   const [chatLoading,  setChatLoading] = useState(false);
//   const chatEnd = useRef(null);

//   useEffect(() => { chatEnd.current?.scrollIntoView({ behavior:'smooth' }); }, [chatHistory, chatLoading]);

//   const enc = () => encodeURIComponent(url);

//   // =========================================================================
//   // ANALYZE — Step 1: clone+index, Step 2: fetch summary, Step 3: contributors
//   // =========================================================================
//   async function analyze() {
//     setLoading(true);
//     setError('');
//     setData(null);
//     setReview('');
//     setChatHistory([]);
//     setContributors([]);

//     try {
//       // ── Step 1: Clone + Index + Save ──────────────────────────────────
//       setLoadingMsg('Cloning and indexing repository…');
//       const analyzeRes = await fetch(`${API}/analyze?url=${enc()}`);

//       if (!analyzeRes.ok) {
//         // Try to parse error as JSON first, fallback to text
//         let errMsg = `HTTP ${analyzeRes.status}`;
//         try {
//           const errJson = await analyzeRes.json();
//           errMsg = errJson.error || errMsg;
//         } catch (_) {
//           errMsg = await analyzeRes.text().catch(() => errMsg);
//         }
//         throw new Error(errMsg);
//       }

//       // analyzeRes body = {"message": "Repository cloned, indexed, and saved successfully!"}
//       // We only use it to confirm success, not for display data
//       await analyzeRes.json();

//       // ── Step 2: Fetch Dashboard Summary ───────────────────────────────
//       setLoadingMsg('Generating AI summary…');
//       const summaryRes = await fetch(`${API}/summary?url=${enc()}`);

//       if (!summaryRes.ok) {
//         let errMsg = `Summary fetch failed — HTTP ${summaryRes.status}`;
//         try {
//           const errJson = await summaryRes.json();
//           errMsg = errJson.error || errMsg;
//         } catch (_) {
//           errMsg = await summaryRes.text().catch(() => errMsg);
//         }
//         throw new Error(errMsg);
//       }

//       // summaryData = { repositoryName, totalCommits, aiSummary, dailyCommitCounts, ... }
//       const summaryData = await summaryRes.json();
//       setData(summaryData);

//       // ── Step 3: Contributors — non-fatal, won't crash if it fails ─────
//       setLoadingMsg('Loading contributors…');
//       try {
//         const cRes = await fetch(`http://localhost:8080/api/contributors?url=${enc()}`);
//         if (cRes.ok) {
//           const cData = await cRes.json();
//           setContributors(Array.isArray(cData) ? cData : []);
//         }
//       } catch (_) {
//         // Contributors are optional — silently ignore
//       }

//     } catch (err) {
//       setError(`Analysis failed — ${err.message || 'Is your Spring Boot server running on :8080?'}`);
//     } finally {
//       setLoading(false);
//       setLoadingMsg('');
//     }
//   }

//   // =========================================================================
//   // CODE REVIEW
//   // =========================================================================
//   async function runReview() {
//     setReviewing(true);
//     setReview('');
//     try {
//       const res = await fetch(`${API}/code-review?url=${enc()}`);
//       if (!res.ok) {
//         const msg = await res.text().catch(() => '');
//         throw new Error(msg || `HTTP ${res.status}`);
//       }
//       setReview(await res.text());
//     } catch (err) {
//       setError(`Code review failed — ${err.message || 'Check IntelliJ console.'}`);
//     } finally {
//       setReviewing(false);
//     }
//   }

//   // =========================================================================
//   // RAG CHAT
//   // =========================================================================
//   async function askChat() {
//     if (!question.trim()) return;
//     const userQ = question;
//     setQuestion('');
//     setChatHistory(prev => [...prev, { role:'user', content:userQ }]);
//     setChatLoading(true);
//     try {
//       const res = await fetch('http://localhost:8080/api/chat', {
//         method: 'POST',
//         headers: { 'Content-Type': 'application/json' },
//         body: JSON.stringify({ question: userQ, url }),
//       });
//       if (!res.ok) {
//         const msg = await res.text().catch(() => '');
//         throw new Error(msg || `HTTP ${res.status}`);
//       }
//       const answer = await res.text();
//       setChatHistory(prev => [...prev, { role:'ai', content:answer }]);
//     } catch (err) {
//       setChatHistory(prev => [...prev, {
//         role:'ai',
//         content:`⚠ Chat failed: ${err.message || 'Ensure backend is running.'}`,
//       }]);
//     } finally {
//       setChatLoading(false);
//     }
//   }

//   return (
//     <>
//       <style>{STYLES}</style>

//       {/* Ambient background orbs */}
//       <div style={{ position:'fixed', inset:0, pointerEvents:'none', zIndex:0, overflow:'hidden' }}>
//         <div style={{ position:'absolute', top:'-10%', right:'10%', width:480, height:480, borderRadius:'50%',
//           background:'radial-gradient(circle, rgba(139,127,255,0.065) 0%, transparent 68%)',
//           animation:'floatA 10s ease-in-out infinite' }} />
//         <div style={{ position:'absolute', bottom:'-5%', left:'-8%', width:360, height:360, borderRadius:'50%',
//           background:'radial-gradient(circle, rgba(52,211,153,0.045) 0%, transparent 68%)',
//           animation:'floatB 13s ease-in-out infinite' }} />
//         <div style={{ position:'absolute', top:'40%', left:'40%', width:260, height:260, borderRadius:'50%',
//           background:'radial-gradient(circle, rgba(251,191,36,0.025) 0%, transparent 65%)',
//           animation:'floatA 17s ease-in-out infinite reverse' }} />
//       </div>

//       <div style={{ minHeight:'100vh', padding:'3rem 1.5rem 8rem', position:'relative', zIndex:1,
//         fontFamily:"'Outfit', sans-serif", maxWidth:920, margin:'0 auto' }}>

//         {/* ─── Header ─── */}
//         <header style={{ marginBottom:'2.8rem' }} className="fu">
//           <div style={{ display:'flex', alignItems:'center', gap:12, marginBottom:10 }}>
//             <div style={{ width:40, height:40, borderRadius:12, flexShrink:0,
//               background:`linear-gradient(145deg, ${C.violet}, #5b4de8)`,
//               display:'flex', alignItems:'center', justifyContent:'center',
//               boxShadow:'0 4px 18px rgba(139,127,255,0.38)',
//             }}>
//               <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
//                 <circle cx="10" cy="10" r="3.8" fill="white" fillOpacity=".92"/>
//                 <circle cx="10" cy="10" r="7.5" stroke="white" strokeOpacity=".35" strokeWidth="1.1"/>
//                 <circle cx="10" cy="10" r="4.8" stroke="white" strokeOpacity=".18" strokeWidth="3"/>
//                 {[[10,1.5,10,4],[10,16,10,18.5],[1.5,10,4,10],[16,10,18.5,10]].map(([x1,y1,x2,y2],i) => (
//                   <line key={i} x1={x1} y1={y1} x2={x2} y2={y2} stroke="white" strokeOpacity=".55" strokeWidth="1.1" strokeLinecap="round"/>
//                 ))}
//               </svg>
//             </div>
//             <h1 style={{ fontSize:28, fontWeight:800, color:C.text, letterSpacing:'-.035em' }}>GitInsight</h1>
//             <Badge>Beta</Badge>
//           </div>
//           <p style={{ fontSize:13.5, color:C.textMd, paddingLeft:52, letterSpacing:'.01em' }}>
//             AI-powered codebase health · commit analytics · static review
//           </p>
//         </header>

//         {/* ─── URL Input ─── */}
//         <div className="panel fu" style={{ padding:'1.3rem 1.5rem', marginBottom:'1rem', animationDelay:'.04s' }}>
//           <div style={{ fontSize:10, color:C.textLo, letterSpacing:'.12em', textTransform:'uppercase', marginBottom:9 }}>
//             Repository URL
//           </div>
//           <div style={{ display:'flex', gap:10 }}>
//             <div style={{ flex:1, position:'relative' }}>
//               <span style={{ position:'absolute', left:12, top:'50%', transform:'translateY(-50%)', color:C.textLo, fontSize:14, pointerEvents:'none' }}>⌥</span>
//               <input value={url} onChange={e => setUrl(e.target.value)}
//                 onKeyDown={e => e.key === 'Enter' && analyze()}
//                 placeholder="https://github.com/user/repo"
//                 style={{ width:'100%', background:C.bg, border:`1px solid ${C.border}`, borderRadius:10,
//                   padding:'10px 14px 10px 30px', fontSize:12.5, color:C.text, outline:'none',
//                   fontFamily:"'Fira Code', monospace", transition:'border-color .2s',
//                 }}
//                 onFocus={e => e.target.style.borderColor = C.violet}
//                 onBlur={e  => e.target.style.borderColor = C.border}
//               />
//             </div>
//             <Btn onClick={analyze} disabled={loading}>
//               {loading ? <><span className="spin" />{loadingMsg || 'Analyzing…'}</> : <>✦ Generate Insight</>}
//             </Btn>
//           </div>
//         </div>

//         {/* ─── Error ─── */}
//         {error && (
//           <div className="fi" style={{ background:'rgba(239,68,68,0.07)', border:'1px solid rgba(239,68,68,0.22)',
//             borderRadius:12, padding:'11px 16px', fontSize:13, color:'#fca5a5',
//             marginBottom:'1rem', display:'flex', alignItems:'center', gap:8 }}>
//             <span>⚠</span>{error}
//           </div>
//         )}

//         {/* ─── Loading skeletons ─── */}
//         {loading && (
//           <div style={{ display:'flex', flexDirection:'column', gap:'1rem' }} className="fi">
//             <div style={{ fontSize:12.5, color:C.textMd, textAlign:'center', marginBottom:4, letterSpacing:'.04em' }}>
//               {loadingMsg}
//             </div>
//             {[180, 140, 200].map((h, i) => (
//               <div key={i} className="skeleton" style={{ height:h, animationDelay:`${i*0.1}s` }} />
//             ))}
//           </div>
//         )}

//         {/* ─── Dashboard ─── */}
//         {data && !loading && (
//           <div style={{ display:'flex', flexDirection:'column', gap:'1rem' }}>

//             {/* Row 1: repo name + commit count */}
//             <div style={{ display:'grid', gridTemplateColumns:'1fr auto', gap:'1rem',
//               animation:'fadeUp .4s cubic-bezier(.22,1,.36,1) both', animationDelay:'.05s' }}>
//               <div className="panel" style={{ padding:'1.3rem 1.6rem' }}>
//                 <div style={{ fontSize:10, color:C.textLo, letterSpacing:'.12em', textTransform:'uppercase', marginBottom:7 }}>Repository</div>
//                 <div style={{ fontSize:20, fontWeight:700, color:C.text, fontFamily:"'Fira Code',monospace",
//                   overflow:'hidden', textOverflow:'ellipsis', whiteSpace:'nowrap' }}>
//                   {data.repositoryName || url.split('/').pop()}
//                 </div>
//                 <div style={{ fontSize:11.5, color:C.textLo, marginTop:5, fontFamily:"'Fira Code',monospace" }}>
//                   {url.replace('https://github.com/', '')}
//                 </div>
//               </div>
//               <div className="panel" style={{ padding:'1.3rem 1.8rem', display:'flex', flexDirection:'column',
//                 alignItems:'center', justifyContent:'center', gap:5, minWidth:140 }}>
//                 <div style={{ fontSize:10, color:C.textLo, letterSpacing:'.12em', textTransform:'uppercase' }}>Total Commits</div>
//                 <div style={{ fontSize:42, fontWeight:800, color:C.text, fontFamily:"'Fira Code',monospace", lineHeight:1 }}>
//                   {data.totalCommits ?? '—'}
//                 </div>
//               </div>
//             </div>

//             {/* Row 2: AI summary + activity heatmap */}
//             <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr', gap:'1rem',
//               animation:'fadeUp .4s cubic-bezier(.22,1,.36,1) both', animationDelay:'.09s' }}>
//               <div className="panel" style={{ padding:'1.4rem 1.5rem', borderLeft:`2px solid ${C.violet}` }}>
//                 <Label icon="✦">AI Summary</Label>
//                 <p style={{ fontSize:13.5, color:C.textMd, lineHeight:1.85, margin:0 }}>
//                   {data.aiSummary || 'No summary available.'}
//                 </p>
//               </div>

//               <div style={{ display:'flex', flexDirection:'column', gap:'1rem' }}>
//                 <div className="panel" style={{ padding:'1.4rem 1.5rem' }}>
//                   <Label icon="◫">30-Day Activity</Label>
//                   <Heatmap dailyCounts={data.dailyCommitCounts} />
//                 </div>

//                 {contributors.length > 0 && (
//                   <div className="panel fu" style={{ padding:'1.4rem 1.5rem', animationDelay:'.14s' }}>
//                     <Label icon="★">Top Contributors</Label>
//                     <Contributors list={contributors} />
//                   </div>
//                 )}
//               </div>
//             </div>

//             <Rule />

//             {/* ─── Code Review ─── */}
//             {!review ? (
//               <div className="panel fu" style={{ display:'flex', justifyContent:'space-between', alignItems:'center',
//                 padding:'1.25rem 1.6rem', animationDelay:'.13s' }}>
//                 <div>
//                   <div style={{ fontSize:14.5, fontWeight:700, color:C.text, marginBottom:5 }}>Deep Code Review</div>
//                   <div style={{ fontSize:13, color:C.textMd }}>Analyse Big-O complexity, bugs & clean-code violations</div>
//                 </div>
//                 <Btn onClick={runReview} disabled={reviewing} variant="ghost">
//                   {reviewing ? <><span className="spin" />Analysing…</> : <>⌕ Run Analysis</>}
//                 </Btn>
//               </div>
//             ) : (
//               <div className="panel fu" style={{ padding:'1.5rem', borderTop:`2px solid ${C.violet}` }}>
//                 <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center',
//                   marginBottom:'1.2rem', paddingBottom:'1rem', borderBottom:`1px solid ${C.border}` }}>
//                   <div style={{ display:'flex', alignItems:'center', gap:10 }}>
//                     <div style={{ width:32, height:32, borderRadius:9, background:C.violetLo, border:`1px solid ${C.violetMd}`,
//                       display:'flex', alignItems:'center', justifyContent:'center', fontSize:15 }}>👨‍💻</div>
//                     <span style={{ fontSize:14, fontWeight:700, color:C.text }}>Senior Engineer Feedback</span>
//                   </div>
//                   <Btn onClick={runReview} disabled={reviewing} variant="ghost">↺ Re-analyse</Btn>
//                 </div>
//                 <div className="md"><ReactMarkdown>{cleanMd(review)}</ReactMarkdown></div>
//               </div>
//             )}

//             <Rule />

//             {/* ─── RAG Chat ─── */}
//             <div className="panel fu" style={{ padding:'1.5rem', borderTop:`2px solid ${C.emerald}`, animationDelay:'.16s' }}>
//               <div style={{ display:'flex', alignItems:'center', gap:10,
//                 paddingBottom:'1rem', marginBottom:'.8rem', borderBottom:`1px solid ${C.border}` }}>
//                 <div style={{ width:32, height:32, borderRadius:9, background:C.emeraldLo,
//                   border:`1px solid rgba(52,211,153,0.25)`,
//                   display:'flex', alignItems:'center', justifyContent:'center', fontSize:15 }}>🤖</div>
//                 <span style={{ fontSize:14, fontWeight:700, color:C.text }}>Ask the Codebase</span>
//                 <Badge color={C.emerald} bg={C.emeraldLo} border="rgba(52,211,153,0.28)">RAG</Badge>
//               </div>

//               <div className="chat-scroll" style={{ maxHeight:380, overflowY:'auto', display:'flex',
//                 flexDirection:'column', gap:'0.7rem', paddingRight:6, marginBottom:'1rem' }}>
//                 {chatHistory.length === 0 && !chatLoading && (
//                   <div style={{ textAlign:'center', color:C.textLo, fontSize:12.5, padding:'2rem 0' }}>
//                     Try: <em style={{ color:C.textMd }}>"How is the Dutch National Flag algorithm implemented?"</em>
//                   </div>
//                 )}
//                 {chatHistory.map((msg, i) => (
//                   <div key={i} className="fi" style={{ alignSelf:msg.role==='user'?'flex-end':'flex-start', maxWidth:'84%' }}>
//                     {msg.role === 'user' ? (
//                       <div style={{ background:`linear-gradient(135deg, ${C.violet}, #5b4de8)`,
//                         color:'#fff', padding:'10px 15px', borderRadius:'14px 14px 3px 14px',
//                         fontSize:13.5, lineHeight:1.6, boxShadow:'0 3px 12px rgba(139,127,255,0.28)' }}>
//                         {msg.content}
//                       </div>
//                     ) : (
//                       <div style={{ background:C.raised, border:`1px solid ${C.border}`,
//                         color:C.text, padding:'12px 16px', borderRadius:'3px 14px 14px 14px', fontSize:13.5 }}>
//                         <div className="md"><ReactMarkdown>{cleanMd(msg.content)}</ReactMarkdown></div>
//                       </div>
//                     )}
//                   </div>
//                 ))}
//                 {chatLoading && (
//                   <div style={{ alignSelf:'flex-start', background:C.raised, border:`1px solid ${C.border}`,
//                     padding:'11px 16px', borderRadius:'3px 14px 14px 14px',
//                     display:'flex', alignItems:'center', gap:9 }}>
//                     <span className="spin" style={{ borderTopColor:C.emerald, borderColor:C.emeraldLo }} />
//                     <span style={{ fontSize:12.5, color:C.textMd }}>Searching vector database…</span>
//                   </div>
//                 )}
//                 <div ref={chatEnd} />
//               </div>

//               <div style={{ display:'flex', gap:9 }}>
//                 <input value={question} onChange={e => setQuestion(e.target.value)}
//                   onKeyDown={e => e.key === 'Enter' && !chatLoading && askChat()}
//                   placeholder="Ask anything about this repository…"
//                   style={{ flex:1, background:C.bg, border:`1px solid ${C.border}`, borderRadius:10,
//                     padding:'11px 15px', fontSize:13.5, color:C.text, outline:'none',
//                     fontFamily:"'Outfit',sans-serif", transition:'border-color .2s' }}
//                   onFocus={e => e.target.style.borderColor = C.emerald}
//                   onBlur={e  => e.target.style.borderColor = C.border}
//                 />
//                 <Btn onClick={askChat} disabled={chatLoading || !question.trim()} accentColor={C.emerald}>
//                   Send ↗
//                 </Btn>
//               </div>
//             </div>

//           </div>
//         )}
//       </div>
//     </>
//   );
// }






// import { useState } from 'react';
// import ReactMarkdown from 'react-markdown';

// // Robust Heatmap logic component
// const CommitHeatmap = ({ dailyCounts }) => {
//   // Convert object to array for mapping
//   const entries = Object.entries(dailyCounts || {});
//   // Slice to get the last 30 entries
//   const data = entries.slice(-30);

//   const getColor = (count) => {
//     if (!count || count === 0) return 'bg-slate-100';
//     if (count === 1) return 'bg-indigo-200';
//     if (count === 2) return 'bg-indigo-400';
//     return 'bg-indigo-600';
//   };

//   return (
//     <div className="flex flex-wrap gap-1">
//       {data.length > 0 ? (
//         data.map(([date, count]) => (
//           <div 
//             key={date} 
//             title={`${date}: ${count} commits`}
//             className={`w-4 h-4 rounded-sm ${getColor(count)} transition-all hover:scale-110`} 
//           />
//         ))
//       ) : (
//         <p className="text-sm text-slate-400 italic">No activity data found.</p>
//       )}
//     </div>
//   );
// };

// function App() {
//   const [repoUrl, setRepoUrl] = useState('https://github.com/Nagendrasriram/Practice-for-Dsa-using-Java-Arrays'); 
//   const [dashboardData, setDashboardData] = useState(null);
//   const [error, setError] = useState('');
//   const [isLoading, setIsLoading] = useState(false);
  
//   const [codeReview, setCodeReview] = useState('');
//   const [isReviewLoading, setIsReviewLoading] = useState(false);

//   const fetchAiSummary = async () => {
//     setIsLoading(true);
//     setError('');
//     setDashboardData(null);
//     setCodeReview(''); 
    
//     try {
//       const encodedUrl = encodeURIComponent(repoUrl);
//       const response = await fetch(`http://localhost:8080/api/repositories/analyze?url=${encodedUrl}`);
//       if (!response.ok) throw new Error("Failed to fetch data");
//       const data = await response.json();
//       setDashboardData(data);
//     } catch (err) {
//       setError("🚨 Error connecting to backend. Check your IntelliJ console!");
//     } finally {
//       setIsLoading(false);
//     }
//   };

//   const fetchDeepCodeReview = async () => {
//     setIsReviewLoading(true);
//     setCodeReview('');
    
//     try {
//       const encodedUrl = encodeURIComponent(repoUrl);
//       const response = await fetch(`http://localhost:8080/api/repositories/code-review?url=${encodedUrl}`);
//       if (!response.ok) throw new Error("Failed to fetch code review");
//       const textData = await response.text();
//       setCodeReview(textData);
//     } catch (err) {
//       setError("🚨 Code Review Failed. Check IntelliJ console for the error.");
//     } finally {
//       setIsReviewLoading(false);
//     }
//   };

//   return (
//     <div className="min-h-screen bg-slate-50 p-8 font-sans pb-20">
//       <div className="max-w-5xl mx-auto mb-8">
//         <h1 className="text-4xl font-extrabold text-slate-900 tracking-tight">GitInsight</h1>
//         <p className="text-slate-500 mt-2 text-lg">AI-Powered Codebase Health & Analytics</p>
//       </div>

//       <div className="max-w-5xl mx-auto bg-white rounded-2xl shadow-sm border border-slate-200 p-6 mb-8">
//         <div className="flex gap-4 items-end">
//           <div className="flex-1">
//             <label className="block text-sm font-semibold text-slate-700 mb-2">Target GitHub Repository</label>
//             <input 
//               type="text" 
//               value={repoUrl}
//               onChange={(e) => setRepoUrl(e.target.value)}
//               className="w-full border border-slate-300 rounded-xl px-4 py-3 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 outline-none transition-all"
//               placeholder="https://github.com/user/repo"
//             />
//           </div>
//           <button 
//             onClick={fetchAiSummary}
//             disabled={isLoading}
//             className="bg-indigo-600 hover:bg-indigo-700 disabled:bg-indigo-300 text-white font-bold py-3 px-8 rounded-xl transition-all whitespace-nowrap shadow-sm"
//           >
//             {isLoading ? 'Analyzing Pipeline...' : 'Generate Insight'}
//           </button>
//         </div>
//       </div>

//       {error && (
//         <div className="max-w-5xl mx-auto bg-red-50 text-red-700 p-4 rounded-xl border border-red-200 mb-8 font-medium">
//           {error}
//         </div>
//       )}

//       {dashboardData && !isLoading && (
//         <div className="max-w-5xl mx-auto space-y-6">
//           <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
//             <div className="col-span-2 bg-white rounded-2xl shadow-sm border border-slate-200 p-6 flex flex-col justify-center">
//               <p className="text-sm font-bold text-slate-400 uppercase tracking-wider mb-1">Target Project</p>
//               <h3 className="text-2xl font-bold text-slate-800 truncate">{dashboardData.repositoryName}</h3>
//             </div>
//             <div className="bg-gradient-to-br from-indigo-500 to-purple-600 rounded-2xl shadow-sm p-6 text-white flex flex-col justify-center items-center">
//               <p className="text-indigo-100 font-medium mb-1">Total Commits</p>
//               <h3 className="text-5xl font-black">{dashboardData.totalCommits}</h3>
//             </div>
//           </div>

//           <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
//             <div className="bg-slate-900 rounded-2xl shadow-lg border border-slate-800 p-8 text-white">
//               <div className="flex items-center gap-3 mb-6">
//                 <span className="bg-indigo-500 p-2 rounded-lg text-white">✨</span>
//                 <h2 className="text-2xl font-bold text-slate-50">AI Executive Summary</h2>
//               </div>
//               <p className="text-slate-300 leading-relaxed text-lg">
//                 {dashboardData.aiSummary}
//               </p>
//             </div>

//             <div className="bg-white rounded-2xl shadow-sm border border-slate-200 p-6">
//               <h3 className="text-lg font-bold text-slate-800 mb-6">30-Day Activity Heatmap</h3>
//               <CommitHeatmap dailyCounts={dashboardData.dailyCommitCounts} />
//               <div className="text-xs text-slate-400 flex justify-between mt-4">
//                   <span>Less Activity</span>
//                   <div className="flex gap-1">
//                       <div className="w-3 h-3 bg-slate-100 rounded-sm"></div>
//                       <div className="w-3 h-3 bg-indigo-200 rounded-sm"></div>
//                       <div className="w-3 h-3 bg-indigo-600 rounded-sm"></div>
//                   </div>
//                   <span>More Activity</span>
//               </div>
//             </div>
//           </div>

//           <div className="bg-white rounded-2xl shadow-sm border border-slate-200 p-8 flex flex-col items-center text-center">
//             <h3 className="text-xl font-bold text-slate-800 mb-2">Deep Code Review</h3>
//             <p className="text-slate-500 mb-6 max-w-lg">Let our AI Senior Staff Engineer analyze your code for Big-O inefficiencies, bugs, and clean code violations.</p>
//             <button 
//               onClick={fetchDeepCodeReview}
//               disabled={isReviewLoading}
//               className="bg-slate-900 hover:bg-slate-800 disabled:bg-slate-400 text-white font-bold py-3 px-8 rounded-xl transition-all shadow-sm"
//             >
//               {isReviewLoading ? 'Analyzing Code...' : '🔍 Run Static Analysis'}
//             </button>
//           </div>

//           {codeReview && (
//              <div className="bg-white rounded-2xl shadow-lg border-2 border-indigo-100 p-8">
//                <div className="flex items-center gap-3 mb-6 border-b border-slate-100 pb-4">
//                  <span className="text-2xl">👨‍💻</span>
//                  <h2 className="text-xl font-bold text-slate-800">Senior Engineer Feedback</h2>
//                </div>
//                <div className="prose prose-slate prose-indigo max-w-none text-slate-700">
//                  <ReactMarkdown>{codeReview}</ReactMarkdown>
//                </div>
//              </div>
//           )}
//         </div>
//       )}
//     </div>
//   )
// }

// export default App;