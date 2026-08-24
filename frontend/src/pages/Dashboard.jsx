import React, { useCallback, useEffect, useState } from 'react'
import { getStats, listLogs, rescan, clearAll } from '../api/client.js'
import StatCards from '../components/StatCards.jsx'
import Filters from '../components/Filters.jsx'
import LogTable from '../components/LogTable.jsx'
import UploadButton from '../components/UploadButton.jsx'

const PAGE_SIZE = 25

export default function Dashboard() {
  const [stats, setStats] = useState(null)
  const [page, setPage] = useState(null)
  const [notice, setNotice] = useState(null)
  const [loading, setLoading] = useState(false)
  const [filters, setFilters] = useState({
    view: 'all', q: '', status: '', sort: 'timestamp', dir: 'asc', page: 0,
  })

  const refresh = useCallback(async () => {
    setLoading(true)
    try {
      const params = {
        page: filters.page, size: PAGE_SIZE, sort: filters.sort, dir: filters.dir,
      }
      if (filters.view === 'anomaly') params.anomaly = true
      if (filters.view === 'normal') params.anomaly = false
      if (filters.q) params.q = filters.q
      if (filters.status) params.status = filters.status
      const [s, p] = await Promise.all([getStats(), listLogs(params)])
      setStats(s); setPage(p)
    } catch (e) {
      setNotice({ type: 'error', text: 'Could not reach the API. Is the backend running on :8080?' })
    } finally {
      setLoading(false)
    }
  }, [filters])

  useEffect(() => { refresh() }, [refresh])

  function onUpload(res, err) {
    if (err) { setNotice({ type: 'error', text: err }); return }
    const issues = res.skipped > 0 ? ` ${res.skipped} rows skipped (validation).` : ''
    setNotice({ type: 'ok', text: `${res.message}${issues}` })
    setFilters((f) => ({ ...f, page: 0 }))
    refresh()
  }

  async function onRescan() {
    setLoading(true)
    try { const r = await rescan(); setNotice({ type: 'ok', text: `Re-scan complete: ${r.anomalies} anomalies.` }); refresh() }
    finally { setLoading(false) }
  }

  async function onClear() {
    if (!window.confirm('Delete all log entries from the database?')) return
    const r = await clearAll()
    setNotice({ type: 'ok', text: `Deleted ${r.deleted} entries.` })
    setFilters((f) => ({ ...f, page: 0 }))
    refresh()
  }

  const statusOptions = stats ? Object.keys(stats.statusDistribution) : []

  return (
    <div className="dashboard">
      <div className="toolbar">
        <h1>Dashboard</h1>
        <div className="toolbar-actions">
          <UploadButton onDone={onUpload} />
          <button className="btn" onClick={onRescan}>Re-scan</button>
          <button className="btn danger-outline" onClick={onClear}>Clear</button>
        </div>
      </div>

      {notice && <div className={`notice ${notice.type}`} onClick={() => setNotice(null)}>{notice.text}</div>}

      <StatCards stats={stats} />

      <Filters value={filters} onChange={setFilters} statusOptions={statusOptions} />

      <div className={`table-wrap ${loading ? 'loading' : ''}`}>
        <LogTable logs={page?.content} />
      </div>

      {page && page.totalPages > 1 && (
        <div className="pagination">
          <button className="btn" disabled={filters.page <= 0}
                  onClick={() => setFilters((f) => ({ ...f, page: f.page - 1 }))}>← Prev</button>
          <span>Page {page.page + 1} of {page.totalPages} · {page.totalElements.toLocaleString()} entries</span>
          <button className="btn" disabled={filters.page >= page.totalPages - 1}
                  onClick={() => setFilters((f) => ({ ...f, page: f.page + 1 }))}>Next →</button>
        </div>
      )}
    </div>
  )
}
