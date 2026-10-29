import { Component } from 'react'

export default class DebugWrapper extends Component {
  static getDerivedStateFromError(error) {
    return { hasError: true, error }
  }

  render() {
    if (this.state?.hasError) {
      return (
        <div style={{ padding: '2rem', color: 'red', fontFamily: 'monospace' }}>
          <h2>Application Crash</h2>
          <pre>{this.state.error?.stack || String(this.state.error)}</pre>
          <details>
            <summary>Details</summary>
            <p>This renders all pages except login.</p>
            <ul>
              <li>
                <a href="/">/ (live stream)</a>
              </li>
              <li>
                <a href="/notifications">Notifications</a>
              </li>
              <li>
                <a href="/settings">Settings</a>
              </li>
              <li>
                <a href="/profile">Profile</a>
              </li>
            </ul>
          </details>
        </div>
      )
    }
    return this.props.children
  }
}
