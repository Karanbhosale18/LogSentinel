import React, { useEffect, useState } from 'react'
import { useParams, useNavigate, useOutletContext } from 'react-router-dom'
import { getLog } from '../api/client.js'
import ScoreBreakdown from '../components/ScoreBreakdown.jsx'
import AiAnalysisCard from '../components/AiAnalysisCard.jsx'

function Field({ label, value, mono }) {
  return (
    <div className="kv">
      <span className="kv-label">{label}</span>
      <span className={`kv-value ${mono ? 'mono' : ''}`}>{value ?? '—'}</span>
    </div>
  )
}

export default function LogDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { meta } = useOutletContext() || {}
  const [log, setLog] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    getLog(id).then(setLog).catch(() => setError('Log entry not found.'))
  }, [id])

  if (error) return <div className="notice error">{error} <button className="btn" onClick={() => navigate('/')}>Back</button></div>
  if (!log) return <div className="empty">Loading…</div>

  return (
    <div className="detail">
      <button className="btn ghost back" onClick={() => navigate(-1)}>← Back</button>

      <div className="detail-head">
        <h1>Log #{log.id}</h1>
        {log.anomaly
          ? <span className="flag big">⚠ Anomaly · {(log.anomalyScore ?? 0).toFixed(2)}</span>
          : <span className="ok-badge">Normal</span>}
      </div>

      <div className="detail-grid">
        <section className="card">
          <h3>Entry</h3>
          <Field label="Timestamp" value={(log.timestamp || '').replace('T', ' ')} mono />
          <Field label="Source IP" value={log.ipAddress} mono />
          <Field label="Request type" value={log.requestType} />
          <Field label="Status code" value={log.statusCode} mono />
          <Field label="User agent" value={log.userAgent} />
          <Field label="Session ID" value={log.sessionId} mono />
          <Field label="Location" value={log.location} />
          {log.message && <Field label="Message" value={log.message} />}
        </section>

        <section className="card">
          <h3>Why was this flagged?</h3>
          {log.anomaly
            ? <>
                <p className="reason">{log.anomalyReason}</p>
                <ScoreBreakdown signals={log.signals} score={log.anomalyScore}
                                threshold={meta?.detectorThreshold} />
              </>
            : <p className="muted">This entry scored below the anomaly threshold. Signal breakdown:
                <ScoreBreakdown signals={log.signals} score={log.anomalyScore}
                                threshold={meta?.detectorThreshold} /></p>}
        </section>
      </div>

      {log.anomaly && <AiAnalysisCard log={log} onUpdated={(ai) => setLog({ ...log, aiAnalysis: ai, analyzed: true })} />}
    </div>
  )
}
