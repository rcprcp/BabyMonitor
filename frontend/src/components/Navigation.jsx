import { Fragment } from 'react'
import { Group, NavLink as MantineNavLink, Stack } from '@mantine/core'
import { NavLink as RouterNavLink, useLocation } from 'react-router-dom'
import { cameraLabel, useCameras } from '../useCameras'

// The desktop sidebar holds every destination; the phone tab bar keeps the four
// daily ones and reaches the rest through Settings.
export const SIDE_DESTINATIONS = [
  { to: '/', label: 'Monitor', end: true, Icon: MonitorIcon },
  { to: '/notifications', label: 'Alerts', Icon: BellIcon },
  { to: '/environment', label: 'Environment', Icon: EnvironmentIcon },
  { to: '/cameras', label: 'Cameras', Icon: CameraIcon },
  { to: '/audio-alerts', label: 'Audio Configuration', Icon: AudioIcon },
  {
    to: '/environment-config',
    label: 'Environment Configuration',
    Icon: EnvironmentConfigIcon,
  },
  { to: '/settings', label: 'Settings', Icon: SettingsIcon },
]

export const MOBILE_DESTINATIONS = [
  { to: '/', label: 'Monitor', end: true, Icon: MonitorIcon },
  { to: '/notifications', label: 'Alerts', Icon: BellIcon },
  { to: '/cameras', label: 'Cameras', Icon: CameraIcon },
  { to: '/settings', label: 'Settings', Icon: SettingsIcon },
]

/** `true` when `pathname` belongs to `destination` (children count). */
export function isActive(pathname, destination) {
  if (destination.end) {
    return pathname === destination.to
  }
  return pathname === destination.to || pathname.startsWith(`${destination.to}/`)
}

// Desktop: a persistent sidebar, with the two camera pages listed under
// Cameras so a named camera is one click away.
export function SideNavigation() {
  const { pathname } = useLocation()
  const { cameras } = useCameras()

  return (
    <Stack gap={4}>
      {SIDE_DESTINATIONS.map((destination) => (
        <Fragment key={destination.to}>
          <MantineNavLink
            component={RouterNavLink}
            to={destination.to}
            end={destination.end}
            label={destination.label}
            leftSection={<destination.Icon />}
            active={isActive(pathname, destination)}
            variant="light"
          />
          {destination.to === '/cameras' &&
            cameras.map((camera) => (
              <MantineNavLink
                key={camera.cameraNumber}
                component={RouterNavLink}
                to={`/cameras/${camera.cameraNumber}`}
                label={cameraLabel(camera)}
                pl={44}
                active={pathname === `/cameras/${camera.cameraNumber}`}
                variant="subtle"
              />
            ))}
        </Fragment>
      ))}
    </Stack>
  )
}

// Mobile: a fixed tab bar. The active tab is the one React Router marks
// `aria-current="page"`, which is also what the CSS colors.
export function BottomNavigation() {
  return (
    <Group h="100%" gap={0} wrap="nowrap" className="bm-tabs">
      {MOBILE_DESTINATIONS.map((destination) => (
        <RouterNavLink
          key={destination.to}
          to={destination.to}
          end={destination.end}
          className={({ isActive: active }) => (active ? 'bm-tab bm-tab-active' : 'bm-tab')}
        >
          <destination.Icon size={22} />
          <span>{destination.label}</span>
        </RouterNavLink>
      ))}
    </Group>
  )
}

const iconProps = {
  viewBox: '0 0 24 24',
  width: 18,
  height: 18,
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.8,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
  'aria-hidden': true,
}

function MonitorIcon() {
  return (
    <svg {...iconProps}>
      <rect x="3" y="4" width="18" height="13" rx="2" />
      <path d="M8 21h8M12 17v4" />
    </svg>
  )
}

function BellIcon() {
  return (
    <svg {...iconProps}>
      <path d="M18 8a6 6 0 1 0-12 0c0 7-3 8-3 8h18s-3-1-3-8" />
      <path d="M13.7 21a2 2 0 0 1-3.4 0" />
    </svg>
  )
}

function CameraIcon() {
  return (
    <svg {...iconProps}>
      <rect x="2" y="6" width="14" height="12" rx="2" />
      <path d="m22 8-6 4 6 4z" />
    </svg>
  )
}

function EnvironmentIcon() {
  return (
    <svg {...iconProps}>
      <path d="M14 14.76V5a2 2 0 1 0-4 0v9.76a4 4 0 1 0 4 0Z" />
    </svg>
  )
}

function AudioIcon() {
  return (
    <svg {...iconProps}>
      <path d="M4 6h16M4 12h16M4 18h16" />
      <circle cx="9" cy="6" r="2" />
      <circle cx="15" cy="12" r="2" />
      <circle cx="7" cy="18" r="2" />
    </svg>
  )
}

function EnvironmentConfigIcon() {
  return (
    <svg {...iconProps}>
      <path d="M14.7 6.3a4 4 0 0 0-5.4 5.4L3 18l3 3 6.3-6.3a4 4 0 0 0 5.4-5.4l-2.3 2.3-2.3-2.3 2.3-2.3z" />
    </svg>
  )
}

function SettingsIcon() {
  return (
    <svg {...iconProps}>
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.9.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.9l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.9.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.9-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.9V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1Z" />
    </svg>
  )
}
