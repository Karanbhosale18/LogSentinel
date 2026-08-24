import React, { useEffect, useState } from 'react'
import { Outlet, Link } from 'react-router-dom'
import { getMeta, getMe, logout as apiLogout } from './api/client.js'
import Login from './pages/Login.jsx'

export default function App() {
  const [meta, setMeta] = useState(null)
  const [user, setUser] = useState(null)        // { username, authenticated }
  const [authChecked, setAuthChecked] = useState(false)

  // Check session on mount
  useEffect(() => {
    getMe()
      .then((res) => { if (res.authenticated) setUser(res); })
      .catch(() => {})
      .finally(() => setAuthChecked(true))
  }, [])

  // Load meta once authenticated
  useEffect(() => {
    if (user) getMeta().then(setMeta).catch(() => setMeta(null))
  }, [user])

  async function handleLogout() {
    try { await apiLogout() } catch (_) { /* ignore */ }
    setUser(null)
    setMeta(null)
  }

  // Show nothing until the initial auth check completes
  if (!authChecked) return <div className="app"><div className="empty">Loading…</div></div>

  // Gate: show login if not authenticated
  if (!user) {
    return <Login onLogin={(res) => setUser(res)} />
  }

  return (
    <div className="app">
      <header className="topbar">
        <Link to="/" className="brand">
          <span className="brand-mark">◭</span>
          <span>Smart Log Analyzer <em>&amp; Anomaly Detector</em></span>
        </Link>
        <div className="topbar-right">
          {meta && (
            <span className={`ai-badge ${meta.aiLive ? 'live' : 'offline'}`} title={`Model: ${meta.aiModel}`}>
              AI: {meta.aiLive ? 'OpenAI (live)' : 'offline fallback'}
            </span>
          )}
          <span className="user-badge">{user.username}</span>
          <button className="btn ghost" onClick={handleLogout}>Logout</button>
        </div>
      </header>
      <main className="content">
        <Outlet context={{ meta }} />
      </main>
      <footer className="footer">
        Detection is performed by a data-driven algorithm; AI is used only to explain flagged entries.
      </footer>
    </div>
  )
}

