import React from 'react'
import ReactDOM from 'react-dom/client'
import { MantineProvider } from '@mantine/core'
import '@mantine/core/styles.css'
import App from './App'
import './styles.css'
import { noPersistenceColorSchemeManager, readThemeHint, theme } from './theme'

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <MantineProvider
      theme={theme}
      defaultColorScheme={readThemeHint()}
      colorSchemeManager={noPersistenceColorSchemeManager}
    >
      <App />
    </MantineProvider>
  </React.StrictMode>,
)
