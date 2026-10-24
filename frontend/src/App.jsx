import { useEffect, useMemo } from 'react'
import { AppShell, MantineProvider, useMantineColorScheme } from '@mantine/core'
import { Notifications } from '@mantine/notifications'
import { BrowserRouter, Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth'
import useNotificationAlerts from './useNotificationAlerts'
import TitleBar from './components/TitleBar'
import LoginPage from './pages/LoginPage'
import StreamPage from './pages/StreamPage'
import NotificationsPage from './pages/NotificationsPage'
import EnvironmentPage from './pages/EnvironmentPage'
import CameraPage from './pages/CameraPage'
import AudioAlertsPage from './pages/AudioAlertsPage'
import SettingsPage from './pages/SettingsPage'
import ProfilePage from './pages/ProfilePage'
import { createAppTheme, noPersistenceColorSchemeManager } from './theme'

// Everything except the login screen sits behind this layout: it renders the
// blue title bar and bounces unauthenticated visitors to /login (remembering
// where they were heading).
function ProtectedLayout() {
  const { isAuthenticated } = useAuth()
  const location = useLocation()

  // Alert the parent to new notifications from whatever page they are on.
  useNotificationAlerts()

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  return (
    <AppShell header={{ height: 60 }} padding="md">
      <AppShell.Header className="bm-titlebar">
        <TitleBar />
      </AppShell.Header>
      <AppShell.Main>
        <Outlet />
      </AppShell.Main>
    </AppShell>
  )
}

// Mantine only reads `defaultColorScheme` when it mounts, so later changes (from
// the Theme selector) are pushed in here instead.
function ColorSchemeSync() {
  const { colorScheme } = useAuth()
  const { setColorScheme } = useMantineColorScheme()

  useEffect(() => {
    setColorScheme(colorScheme)
  }, [colorScheme, setColorScheme])

  return null
}

// The provider order matters: the account's preferences come from AuthProvider
// (which is deliberately Mantine-free), so MantineProvider sits inside it and
// its theme is rebuilt whenever the account's accent color changes.
function ThemedApp() {
  const { colorScheme, themeColor } = useAuth()
  const theme = useMemo(() => createAppTheme(themeColor), [themeColor])

  return (
    <MantineProvider
      theme={theme}
      defaultColorScheme={colorScheme}
      colorSchemeManager={noPersistenceColorSchemeManager}
    >
      <ColorSchemeSync />
      <Notifications position="top-right" limit={3} />
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<ProtectedLayout />}>
          <Route path="/" element={<StreamPage />} />
          <Route path="/notifications" element={<NotificationsPage />} />
          <Route path="/environment" element={<EnvironmentPage />} />
          <Route path="/cameras/1" element={<CameraPage cameraNumber={1} />} />
          <Route path="/cameras/2" element={<CameraPage cameraNumber={2} />} />
          <Route path="/audio-alerts" element={<AudioAlertsPage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="/profile" element={<ProfilePage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </MantineProvider>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <ThemedApp />
      </AuthProvider>
    </BrowserRouter>
  )
}
