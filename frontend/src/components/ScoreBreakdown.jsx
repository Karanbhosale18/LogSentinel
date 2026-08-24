import React from 'react'

/** Shows how each detector signal contributed to the total anomaly score. */
export default function ScoreBreakdown({ signals, score, threshold }) {
  if (!signals || signals.length === 0) return null
  const max = Math.max(...signals.map((s) => s.weight), 1)

  return (
    <div className="breakdown">
      <div className="breakdown-total">
        <span>Anomaly score</span>
        <strong>{(score ?? 0).toFixed(2)}</strong>
        {threshold != null && <span className="muted">threshold {threshold}</span>}
      </div>
      <table className="signal-table">
        <thead>
          <tr><th>Signal</th><th>Raw</th><th>Weight</th><th>Contribution</th></tr>
        </thead>
        <tbody>
          {signals.map((s) => (
            <tr key={s.name} className={s.rawScore > 0 ? 'fired' : 'quiet'}>
              <td>
                <div className="signal-name">{s.label}</div>
                {s.detail && <div className="signal-detail">{s.detail}</div>}
              </td>
              <td className="mono">{s.rawScore.toFixed(2)}</td>
              <td className="mono">{s.weight.toFixed(2)}</td>
              <td style={{ minWidth: 120 }}>
                <div className="mini-bar"><div className="mini-fill" style={{ width: `${(s.weightedScore / max) * 100}%` }} /></div>
                <span className="mono">{s.weightedScore.toFixed(2)}</span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
