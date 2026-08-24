import React from 'react'

function Card({ label, value, sub, tone }) {
  return (
    <div className={`stat-card ${tone || ''}`}>
      <div className="stat-value">{value}</div>
      <div className="stat-label">{label}</div>
      {sub && <div className="stat-sub">{sub}</div>}
    </div>
  )
}

export default function StatCards({ stats }) {
  if (!stats) return null
  return (
    <div className="stat-grid">
      <Card label="Total logs" value={stats.totalLogs.toLocaleString()} />
      <Card label="Anomalies" value={stats.anomalies.toLocaleString()}
            sub={`${stats.anomalyRate}% of traffic`} tone="danger" />
      <Card label="Server errors (5xx)" value={stats.serverErrors.toLocaleString()} tone="warn" />
      <Card label="Distinct sources" value={stats.distinctIps.toLocaleString()} />
    </div>
  )
}
