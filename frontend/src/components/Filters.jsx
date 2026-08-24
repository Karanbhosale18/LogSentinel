import React from 'react'

export default function Filters({ value, onChange, statusOptions }) {
  const set = (patch) => onChange({ ...value, ...patch, page: 0 })

  return (
    <div className="filters">
      <div className="segmented">
        {[['all', 'All'], ['anomaly', 'Anomalies'], ['normal', 'Normal']].map(([k, label]) => (
          <button key={k} className={value.view === k ? 'active' : ''} onClick={() => set({ view: k })}>
            {label}
          </button>
        ))}
      </div>

      <input className="search" placeholder="Search IP, location, method…"
             value={value.q} onChange={(e) => set({ q: e.target.value })} />

      <select value={value.status} onChange={(e) => set({ status: e.target.value })}>
        <option value="">All statuses</option>
        {statusOptions.map((s) => <option key={s} value={s}>HTTP {s}</option>)}
      </select>

      <select value={`${value.sort}:${value.dir}`}
              onChange={(e) => { const [sort, dir] = e.target.value.split(':'); set({ sort, dir }) }}>
        <option value="timestamp:asc">Time ↑</option>
        <option value="timestamp:desc">Time ↓</option>
        <option value="score:desc">Score ↓</option>
        <option value="status:desc">Status ↓</option>
      </select>
    </div>
  )
}
