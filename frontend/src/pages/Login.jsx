import React, { useState } from 'react'
import { login } from '../api/client.js'

export default function Login({ onLogin }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const res = await login(username, password)
      onLogin?.(res)
    } catch (err) {
      setError(err?.response?.status === 401
        ? 'Invalid username or password.'
        : 'Login failed. Is the backend running?')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login-page">
      <form className="login-card" onSubmit={handleSubmit}>
        <div className="login-header">
          <span className="brand-mark login-icon">◭</span>
          <h1>LogSentinel</h1>
          <p className="muted">Smart Log Analyzer &amp; Anomaly Detector</p>
        </div>

        {error && <div className="notice error">{error}</div>}

        <label className="login-label" htmlFor="username">Username</label>
        <input
          id="username"
          className="login-input"
          type="text"
          autoComplete="username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          required
          autoFocus
        />

        <label className="login-label" htmlFor="password">Password</label>
        <input
          id="password"
          className="login-input"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />

        <button className="btn primary login-btn" type="submit" disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </div>
  )
}
