import React, { useState } from 'react'
import { analyzeLog } from '../api/client.js'

export default function AiAnalysisCard({ log, onUpdated }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const ai = log.aiAnalysis

  async function run() {
    setBusy(true); setError(null)
    try {
      const res = await analyzeLog(log.id)
      onUpdated?.(res)
    } catch (e) {
      setError(e?.response?.data?.message || 'AI analysis failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card ai-card">
      <div className="card-head">
        <h3>AI analysis</h3>
        {ai && <span className={`ai-badge ${ai.provider === 'openai' ? 'live' : 'offline'}`}>
          {ai.provider === 'openai' ? `OpenAI · ${ai.model}` : 'offline fallback'}
        </span>}
      </div>

      {!ai && (
        <div className="ai-empty">
          <p>No explanation generated yet. The AI explains an entry <em>after</em> our algorithm has flagged it.</p>
          <button className="btn primary" disabled={busy} onClick={run}>
            {busy ? 'Analyzing…' : 'Analyze with AI'}
          </button>
        </div>
      )}

      {ai && (
        <div className="ai-body">
          <div className="ai-field"><h4>What happened</h4><p>{ai.explanation}</p></div>
          <div className="ai-field"><h4>Likely root cause</h4><p>{ai.rootCause}</p></div>
          <div className="ai-field"><h4>Recommended next step</h4><p>{ai.nextStep}</p></div>
          <button className="btn ghost" disabled={busy} onClick={run}>
            {busy ? 'Regenerating…' : 'Regenerate'}
          </button>
        </div>
      )}

      {error && <div className="error-inline">{error}</div>}
    </section>
  )
}
