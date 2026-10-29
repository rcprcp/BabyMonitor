import { useEffect, useMemo } from 'react'
import { AppShell, Box, MantineProvider, useMantineColorScheme } from '@mantine/core'
import { Notifications } from '@mantine/notifications'
import {
  BrowserRouter,
  Navigate,
  Outlet,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from 'react-router-dom'
import { AuthProvider, useAuth } from './auth'
import useNotificationAlerts from './useNotificationAlerts'
import { OPEN_NOTIFICATIONS_EVENT } from './alerting'
import TitleBar from './components/TitleBar'
import { BottomNavigation, SideNavigation } from './components/Navigation'
import LoginPage from './pages/LoginPage'
import StreamPage from './pages/StreamPage'
import NotificationsPage from './pages/NotificationsPage'
import EnvironmentPage from './pages/EnvironmentPage'
import CameraPage from './pages/CameraPage'
import CamerasPage from './pages/CamerasPage'
import AudioAlertsPage from './pages/AudioAlertsPage'
import SettingsPage from './pages/SettingsPage'
import MorePage from './pages/MorePage'
import OnboardingPage from './pages/OnboardingPage'
import { OnboardingProvider, useOnboarding } from './onboarding'
import { createAppTheme, noPersistenceColorSchemeManager } from './theme'

// Everything except the login screen sits behind this layout: the blue title
// bar, the desktop sidebar or phone tab bar, and a bounce to /login for
// unauthenticated visitors (remembering where they were heading).
function ProtectedLayout() {
  const { isAuthenticated } = useAuth()
  const { complete } = useOnboarding()
  const location = useLocation()
  const navigate = useNavigate()

  // Alert the parent to new notifications from whatever page they are on.
  useNotificationAlerts()

  // An alert toast asks to open the feed; the router is only available here,
  // which is why alerting.js emits an event instead of importing useNavigate.
  useEffect(() => {
    const openNotifications = () => navigate('/notifications')
    window.addEventListener(OPEN_NOTIFICATIONS_EVENT, openNotifications)
    return () => window.removeEventListener(OPEN_NOTIFICATIONS_EVENT, openNotifications)
  }, [navigate])

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  // A fresh install starts in the wizard; every other route bounces there until
  // it is finished or skipped. `complete` defaults to true, so a missing or
  // unreadable setting can never trap the app.
  if (!complete && location.pathname !== '/welcome') {
    return <Navigate to="/welcome" replace />
  }

  return (
    <AppShell
      header={{ height: 60 }}
      navbar={{ width: 230, breakpoint: 'sm', collapsed: { mobile: true } }}
      padding="md"
    >
      <AppShell.Header className="bm-titlebar">
        <TitleBar />
      </AppShell.Header>
      <AppShell.Navbar p="sm">
        <SideNavigation />
      </AppShell.Navbar>
      {/* The tab bar only exists on phones; the sidebar takes over from `sm`. */}
      <AppShell.Main pb={{ base: 88, sm: 'md' }}>
        <Outlet />
      </AppShell.Main>
      <Box component="nav" aria-label="Main" hiddenFrom="sm" className="bm-bottom-nav">
        <BottomNavigation />
      </Box>
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
      <OnboardingProvider>
        <ColorSchemeSync />
        <Notifications position="top-right" limit={3} />
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<ProtectedLayout />}>
            <Route path="/" element={<StreamPage />} />
            <Route path="/notifications" element={<NotificationsPage />} />
            <Route path="/environment" element={<EnvironmentPage />} />
            <Route path="/cameras" element={<CamerasPage />} />
            <Route path="/cameras/1" element={<CameraPage cameraNumber={1} />} />
            <Route path="/cameras/2" element={<CameraPage cameraNumber={2} />} />
            <Route path="/audio-alerts" element={<AudioAlertsPage />} />
            <Route path="/settings" element={<SettingsPage />} />
            {/* The old Personal settings URL is kept as a redirect for deep links. */}
            <Route path="/profile" element={<Navigate to="/settings" replace />} />
            <Route path="/more" element={<MorePage />} />
            <Route path="/welcome" element={<OnboardingPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </OnboardingProvider>
    </MantineProvider>
  )
}

export default function App() {
  return (
    <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <AuthProvider>
        <ThemedApp />
      </AuthProvider>
    </BrowserRouter>
  )
}
