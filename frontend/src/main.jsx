import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App.jsx'
import './styles.css'

const mount = () =>
  createRoot(document.getElementById('root')).render(
    <StrictMode>
      <App />
    </StrictMode>,
  )

if (import.meta.env.VITE_PREVIEW_MOCK === '1') {
  import('./lib/mock-api.js').then(mount)
} else {
  mount()
}
