import { Fragment } from 'react'
import { Group, NavLink as MantineNavLink, Stack } from '@mantine/core'
import { NavLink as RouterNavLink, useLocation } from 'react-router-dom'
import { cameraLabel, useCameras } from '../useCameras'

// The four destinations. Everything else lives under More, so a parent has one
// obvious place for the day-to-day jobs: watch, see what happened, and find a
// camera. The same list feeds the sidebar and the phone tab bar.
export const DESTINATIONS = [
  { to: '/', label: 'Monitor', end: true, Icon: MonitorIcon },
  { to: '/notifications', label: 'Alerts', Icon: BellIcon },
  { to: '/cameras', label: 'Cameras', Icon: CameraIcon },
  { to: '/more', label: 'More', Icon: MoreIcon },
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
      {DESTINATIONS.map((destination) => (
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
      {DESTINATIONS.map((destination) => (
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

function MoreIcon() {
  return (
    <svg {...iconProps}>
      <circle cx="5" cy="12" r="1.6" />
      <circle cx="12" cy="12" r="1.6" />
      <circle cx="19" cy="12" r="1.6" />
    </svg>
  )
}
