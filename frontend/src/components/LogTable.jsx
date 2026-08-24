import React from 'react'
import { useNavigate } from 'react-router-dom'

function statusClass(code) {
  if (code == null) return ''
  if (code >= 500) return 'st-5xx'
  if (code >= 400) return 'st-4xx'
  if (code >= 300) return 'st-3xx'
  return 'st-2xx'
}

function ScoreBar({ score, anomaly }) {
  const pct = Math.round((score || 0) * 100)
  return (
    <div className="scorebar" title={`score ${score ?? 0}`}>
      <div className={`scorebar-fill ${anomaly ? 'danger' : ''}`} style={{ width: `${pct}%` }} />
      <span className="scorebar-text">{(score ?? 0).toFixed(2)}</span>
    </div>
  )
}

export default function LogTable({ logs }) {
  const navigate = useNavigate()
  if (!logs) return null
  if (logs.length === 0) return <div className="empty">No log entries match the current filters.</div>

  return (
    <table className="logtable">
      <thead>
        <tr>
          <th>Timestamp</th><th>Source IP</th><th>Method</th><th>Status</th>
          <th>Location</th><th>Score</th><th></th>
        </tr>
      </thead>
      <tbody>
        {logs.map((l) => (
          <tr key={l.id} className={l.anomaly ? 'row-anomaly' : ''} onClick={() => navigate(`/logs/${l.id}`)}>
            <td className="mono">{(l.timestamp || '').replace('T', ' ')}</td>
            <td className="mono">{l.ipAddress || '—'}</td>
            <td>{l.requestType || '—'}</td>
            <td><span className={`pill ${statusClass(l.statusCode)}`}>{l.statusCode ?? '—'}</span></td>
            <td>{l.location || '—'}</td>
            <td style={{ width: 120 }}><ScoreBar score={l.anomalyScore} anomaly={l.anomaly} /></td>
            <td>{l.anomaly ? <span className="flag" title={l.anomalyReason}>⚠ anomaly</span> : ''}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
