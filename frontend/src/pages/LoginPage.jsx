import { useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Center,
  PasswordInput,
  Stack,
  Text,
  TextInput,
  Title,
} from '@mantine/core'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth'

export default function LoginPage() {
  const { login, isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [name, setName] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const redirectTo = location.state?.from?.pathname || '/'

  if (isAuthenticated) {
    return <Navigate to={redirectTo} replace />
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await login(name.trim(), password)
      navigate(redirectTo, { replace: true })
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Center mih="100vh" p="md">
      <Card
        component="form"
        onSubmit={handleSubmit}
        withBorder
        shadow="md"
        padding="lg"
        w="100%"
        maw={380}
      >
        <Stack>
          <Title order={2}>Sign in</Title>
          <Text c="dimmed" size="sm">
            Sign in to watch the live stream.
          </Text>

          <TextInput
            id="name"
            name="name"
            label="Name"
            autoComplete="username"
            autoFocus
            required
            value={name}
            onChange={(event) => setName(event.currentTarget.value)}
          />

          <PasswordInput
            id="password"
            name="password"
            label="Password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(event) => setPassword(event.currentTarget.value)}
          />

          {error && (
            <Alert color="red" variant="light">
              {error}
            </Alert>
          )}

          <Button type="submit" loading={busy} fullWidth>
            {busy ? 'Signing in…' : 'Sign in'}
          </Button>
        </Stack>
      </Card>
    </Center>
  )
}
