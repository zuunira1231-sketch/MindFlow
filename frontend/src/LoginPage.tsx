import { useState } from 'react'
import './login-page.css'

type Props = {
  onLogin: (username: string, password: string) => Promise<void>
  busy: boolean
  error: string
}

export function LoginPage({ onLogin, busy, error }: Props) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)

  return <div className="login-page">
    <header className="login-header"><span>MindFlow</span></header>
    <main className="login-layout">
      <section className="login-intro"><span>让思考留下线索</span><h1>回到你的认知。</h1><p>整理一路形成的理解，也看见它是怎样走到今天的。</p><div className="login-marker"><i aria-hidden="true" />从已有的理解继续</div></section>
      <section className="login-form-area" aria-label="登录 MindFlow"><h2>登录</h2><p>使用演示账号继续访问。</p>
        <form onSubmit={event => { event.preventDefault(); void onLogin(username.trim(), password) }}>
          <label htmlFor="login-username">账号</label><input id="login-username" type="text" autoComplete="username" value={username} onChange={event => setUsername(event.target.value)} placeholder="请输入账号" disabled={busy} required />
          <label htmlFor="login-password">密码</label><div className="login-password-field"><input id="login-password" type={showPassword ? 'text' : 'password'} autoComplete="current-password" value={password} onChange={event => setPassword(event.target.value)} placeholder="请输入密码" disabled={busy} required /><button type="button" onClick={() => setShowPassword(value => !value)} aria-label={showPassword ? '隐藏密码' : '显示密码'}>{showPassword ? '隐藏' : '显示'}</button></div>
          {error && <p className="login-error" role="alert">{error}</p>}
          <button className="login-submit" type="submit" disabled={busy}>{busy ? '正在登录…' : '进入 MindFlow　→'}</button>
        </form>
      </section>
    </main>
  </div>
}
