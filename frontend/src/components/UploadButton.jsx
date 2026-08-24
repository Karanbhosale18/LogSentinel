import React, { useRef, useState } from 'react'
import { uploadCsv } from '../api/client.js'

export default function UploadButton({ onDone }) {
  const inputRef = useRef(null)
  const [busy, setBusy] = useState(false)

  async function handleFile(e) {
    const file = e.target.files?.[0]
    if (!file) return
    setBusy(true)
    try {
      const res = await uploadCsv(file)
      onDone?.(res, null)
    } catch (err) {
      onDone?.(null, err?.response?.data?.message || 'Upload failed')
    } finally {
      setBusy(false)
      if (inputRef.current) inputRef.current.value = ''
    }
  }

  return (
    <>
      <input ref={inputRef} type="file" accept=".csv,text/csv" hidden onChange={handleFile} />
      <button className="btn primary" disabled={busy} onClick={() => inputRef.current?.click()}>
        {busy ? 'Uploading…' : 'Upload CSV'}
      </button>
    </>
  )
}
