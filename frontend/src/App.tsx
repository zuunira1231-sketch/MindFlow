import { useEffect, useRef, useState } from 'react'
import { api, ApiError } from './api'
import { DomainCognitionView } from './DomainCognitionView'
import { EvolutionOverlay } from './EvolutionOverlay'
import { LoginPage } from './LoginPage'
import type { Domain, DomainCognition, Draft, Plan, PlanChange } from './types'

type Screen = 'home' | 'import' | 'draft' | 'plan' | 'done'
type ImportMode = 'text' | 'pdf'
type Busy = 'import' | 'draft' | 'plan' | 'confirm' | null

const emptyChanges = (plan: Plan) => Object.entries(plan.changes ?? {}).every(([kind, items]) => !Array.isArray(items) || items.every(item => kind === 'domains' && item.operation === 'USE'))
const planDeadline = (value: string) => {
  const match = /^(\d{4})-(\d\d)-(\d\d)T(\d\d):(\d\d):(\d\d)(?:\.(\d+))?/.exec(value)
  if (!match) return Number.NaN
  return new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]), Number(match[4]), Number(match[5]), Number(match[6]), Number((match[7] ?? '').slice(0, 3).padEnd(3, '0'))).getTime()
}
const isExpired = (plan: Plan) => Boolean(plan.expiresAt) && planDeadline(plan.expiresAt) <= Date.now()
const canConfirm = (plan: Plan) => plan.status === 'READY' && !isExpired(plan)
const statusMessage = (plan: Plan) => plan.status === 'STALE' ? '知识库已变化，请返回草稿重新生成方案。' : plan.status === 'EXPIRED' || isExpired(plan) ? '方案已过期，请返回草稿重新生成。' : plan.status === 'APPLIED' ? '这份方案已经沉淀过了，无需再次确认。' : ''

function readable(value: unknown): string {
  if (value == null) return '—'
  if (typeof value === 'string' || typeof value === 'number') return String(value)
  if (Array.isArray(value)) return value.map(readable).join('、')
  return JSON.stringify(value, null, 2)
}

function field(record: unknown, ...names: string[]): unknown {
  if (!record || typeof record !== 'object' || Array.isArray(record)) return undefined
  const object = record as Record<string, unknown>
  return names.map(name => object[name]).find(value => value !== undefined && value !== null)
}

function changeTitle(change: PlanChange, kind: string): string {
  return readable(field(change.after, 'title', 'name', 'domainName', 'description') ?? change.domainName ?? field(change.before, 'title', 'name', 'description') ?? kind)
}

function describeChange(value: unknown, kind: string): string {
  if (value == null) return '无'
  const title = field(value, 'title', 'name', 'domainName')
  const content = field(value, 'description', 'content', 'cognitiveSummary')
  const links = field(value, 'knowledge', 'knowledgeTitles', 'domainNames')
  return [title, content, links].filter(part => part != null && readable(part).trim()).map(readable).join(' · ') || readable(value)
}

function ChangeGroup({ title, items }: { title: string, items: PlanChange[] }) {
  if (!items?.length) return null
  return <section className="review-section">
    <h3>{title} <small>{items.length} 项变更</small></h3>
    {items.map((item, index) => {
      const operation = item.operation === 'CREATE' ? '新增' : item.operation === 'UPDATE' ? '更新' : item.operation === 'USE' ? '使用已有' : '摘要更新'
      const before = item.before
      const after = item.after
      const name = title === '关系' ? `关系 ${index + 1}` : changeTitle(item, title)
      const content = field(after, 'description', 'content') ?? (typeof after === 'string' ? after : null)
      const links = field(after, 'knowledge', 'domainNames')
      return <article className="plan-change" key={index}>
        <div className="plan-change-head"><strong>{name}</strong><em>{operation}</em></div>
        {content != null && <p className="plan-change-body">{readable(content)}</p>}
        {links != null && <p className="plan-change-links">涉及：{readable(links)}</p>}
        {before != null && <details className="plan-before"><summary>查看变更前</summary><p>{describeChange(before, title)}</p></details>}
      </article>
    })}
  </section>
}

function App() {
  const [screen, setScreen] = useState<Screen>('home')
  const [authStatus, setAuthStatus] = useState<'checking' | 'guest' | 'authenticated'>('checking')
  const [loginError, setLoginError] = useState('')
  const [loginBusy, setLoginBusy] = useState(false)
  const [logoutBusy, setLogoutBusy] = useState(false)
  const [mode, setMode] = useState<ImportMode>('text')
  const [text, setText] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [draft, setDraft] = useState<Draft | null>(null)
  const [plan, setPlan] = useState<Plan | null>(null)
  const [domains, setDomains] = useState<Domain[]>([])
  const [domainSummaryExpanded, setDomainSummaryExpanded] = useState(false)
  const [busy, setBusy] = useState<Busy>(null)
  const [error, setError] = useState('')
  const [domainError, setDomainError] = useState('')
  const [draftDirty, setDraftDirty] = useState(false)
  const [activeDomain, setActiveDomain] = useState<Domain | null>(null)
  const [domainCognition, setDomainCognition] = useState<DomainCognition | null>(null)
  const [domainLoading, setDomainLoading] = useState(false)
  const [domainDetailError, setDomainDetailError] = useState('')
  const [selectedKnowledgeId, setSelectedKnowledgeId] = useState<number | null>(null)
  const [evolutionScope, setEvolutionScope] = useState<Domain | null | undefined>(undefined)
  const pendingPlanRequestId = useRef<string | null>(null)

  async function loadDomains() {
    try { setDomains(await api.domains()); setDomainError('') }
    catch (reason) { setDomainError(messageOf(reason)) }
  }

  async function openDomain(domain: Domain, knowledgeId: number | null = null) {
    setActiveDomain(domain)
    setDomainSummaryExpanded(false)
    setSelectedKnowledgeId(knowledgeId)
    setDomainCognition(null)
    setDomainDetailError('')
    setDomainLoading(true)
    try {
      const result = await api.domainCognition(domain.id)
      setDomainCognition(result)
      setActiveDomain(result.domain)
    } catch (reason) { setDomainDetailError(messageOf(reason)) }
    finally { setDomainLoading(false) }
  }

  async function loadAfterLogin() {
    await loadDomains()
    const planId = new URLSearchParams(window.location.search).get('planId')
    if (planId) {
      try { setPlan(await api.getPlan(planId)); setScreen('plan') }
      catch (reason) { setError(messageOf(reason)); setScreen('home') }
    }
  }

  useEffect(() => {
    let cancelled = false
    api.setUnauthorizedHandler(() => {
      setAuthStatus('guest')
      setDraft(null)
      setPlan(null)
      setDomains([])
      setActiveDomain(null)
      setDomainCognition(null)
      setEvolutionScope(undefined)
      setScreen('home')
      setLoginError('登录状态已失效，请重新登录。')
    })
    void (async () => {
      try {
        const me = await api.me()
        if (cancelled) return
        if (!me.authenticated) { setAuthStatus('guest'); return }
        await api.csrf()
        if (cancelled) return
        setAuthStatus('authenticated')
        await loadAfterLogin()
      } catch (reason) { if (!cancelled) { setLoginError(messageOf(reason)); setAuthStatus('guest') } }
    })()
    return () => { cancelled = true; api.setUnauthorizedHandler(null) }
  }, [])

  async function login(username: string, password: string) {
    if (!username || !password) return setLoginError('请填写账号和密码。')
    setLoginError('')
    setLoginBusy(true)
    try {
      await api.login(username, password)
      await api.csrf()
      setAuthStatus('authenticated')
      await loadAfterLogin()
    } catch (reason) { setLoginError(messageOf(reason)) }
    finally { setLoginBusy(false) }
  }

  async function logout() {
    setLogoutBusy(true)
    try {
      await api.logout()
      setAuthStatus('guest')
      setDraft(null)
      setPlan(null)
      setDomains([])
      setActiveDomain(null)
      setDomainCognition(null)
      setEvolutionScope(undefined)
      setScreen('home')
      window.history.replaceState(null, '', window.location.pathname)
    } catch (reason) { setError(messageOf(reason)) }
    finally { setLogoutBusy(false) }
  }

  function messageOf(reason: unknown) {
    return reason instanceof Error ? reason.message : '发生未知错误，请稍后重试。'
  }

  async function importConversation() {
    setError('')
    if (mode === 'text' && !text.trim()) return setError('请先粘贴对话内容。')
    if (mode === 'pdf') {
      if (!file) return setError('请先选择 PDF 文件。')
      if (file.type !== 'application/pdf' && !file.name.toLowerCase().endsWith('.pdf')) return setError('请选择 PDF 文件。')
      if (file.size > 15_000_000) return setError('PDF 文件不能超过 15 MB。')
    }
    try {
      setBusy('import')
      const imported = mode === 'text' ? await api.importText(text.trim()) : await api.importPdf(file!)
      setBusy('draft')
      const result = await api.draft(imported.conversationId)
      setDraft({ ...result, domain_preview: result.domain_preview ?? [], knowledge_preview: result.knowledge_preview ?? [], relation_preview: result.relation_preview ?? [], evolution_preview: result.evolution_preview ?? [] })
      setDraftDirty(false)
      setScreen('draft')
    } catch (reason) { setError(messageOf(reason)) }
    finally { setBusy(null) }
  }

  function editDraft(next: Draft) { pendingPlanRequestId.current = null; setDraft(next); setDraftDirty(true); setError('') }

  function deleteKnowledge(tempId: string) {
    if (!draft) return
    const linked = draft.relation_preview.some(item => item.knowledge_temp_ids.includes(tempId)) || draft.evolution_preview.some(item => item.knowledge_temp_ids.includes(tempId))
    if (linked) return setError('这条知识仍被关系或演化引用，请先修改或删除相关内容。')
    editDraft({ ...draft, knowledge_preview: draft.knowledge_preview.filter(item => item.temp_id !== tempId) })
  }

  function deleteDomain(tempId: string | null) {
    if (!draft) return
    editDraft({ ...draft,
      domain_preview: draft.domain_preview.filter(item => item.temp_id !== tempId),
      knowledge_preview: draft.knowledge_preview.map(item => ({ ...item, domain_temp_ids: item.domain_temp_ids?.filter(id => id !== tempId) ?? [] })),
    })
  }

  async function generatePlan() {
    if (!draft || busy) return
    setError('')
    if (!draft.title.trim() || !draft.summary.trim()) return setError('请填写草稿标题和摘要。')
    if (draft.knowledge_preview.some(item => !item.title.trim() || !item.description.trim())) return setError('每条知识都需要标题和描述。')
    if (draft.domain_preview.some(item => item.id === null && !item.name.trim())) return setError('待新建领域需要填写名称。')
    const knowledgeIds = new Set(draft.knowledge_preview.map(item => item.temp_id))
    const newDomainIds = new Set(draft.domain_preview.filter(item => item.id === null && item.temp_id).map(item => item.temp_id))
    if (draft.relation_preview.some(item => item.knowledge_temp_ids.length < 2)) return setError('每条关系至少需要关联两条知识。')
    if (draft.evolution_preview.some(item => item.knowledge_temp_ids.length < 1)) return setError('每条演化至少需要关联一条知识。')
    if (draft.relation_preview.some(item => item.knowledge_temp_ids.some(id => !knowledgeIds.has(id))) || draft.evolution_preview.some(item => item.knowledge_temp_ids.some(id => !knowledgeIds.has(id)))) return setError('关系或演化引用了已删除的知识，请先处理。')
    if (draft.knowledge_preview.some(item => item.domain_temp_ids?.some(id => !newDomainIds.has(id)))) return setError('有知识引用了已取消的新领域，请先处理。')
    try {
      setBusy('plan')
      pendingPlanRequestId.current ??= crypto.randomUUID()
      const result = await api.createPlan(draft, pendingPlanRequestId.current)
      pendingPlanRequestId.current = null
      setPlan(result)
      window.history.replaceState(null, '', `?planId=${encodeURIComponent(result.planId)}`)
      setScreen('plan')
    } catch (reason) { setError(messageOf(reason)) }
    finally { setBusy(null) }
  }

  async function confirmPlan() {
    if (!plan || busy || !canConfirm(plan)) return
    setError('')
    try {
      setBusy('confirm')
      await api.confirmPlan(plan.planId)
      window.history.replaceState(null, '', window.location.pathname)
      setScreen('done')
      await loadDomains()
    } catch (reason) {
      if (reason instanceof ApiError && reason.status === 409) {
        setError('方案已过期或知识库已变化，请返回草稿重新生成。')
        try { setPlan(await api.getPlan(plan.planId)) } catch { /* 保留原错误信息 */ }
      }
      else setError(messageOf(reason))
    } finally { setBusy(null) }
  }

  function closeFlow() { setError(''); setScreen('home') }
  function openFlow() { setError(''); setScreen(draft ? plan ? 'plan' : 'draft' : 'import') }
  function backToDraft() {
    if (!draft) { setPlan(null); window.history.replaceState(null, '', window.location.pathname); setError('刷新后当前草稿未保留，请重新导入对话。'); setScreen('import'); return }
    setPlan(null)
    window.history.replaceState(null, '', window.location.pathname)
    setError('')
    setScreen('draft')
  }

  if (authStatus === 'checking') return <div className="auth-checking">正在确认登录状态…</div>
  if (authStatus === 'guest') return <LoginPage onLogin={login} busy={loginBusy} error={loginError} />

  return <div className="app">
    <header className="site-header"><div className="site-inner"><span className="logo">MindFlow</span><div className="header-actions"><nav><button className={evolutionScope === undefined ? 'nav-active' : ''} type="button" onClick={() => setActiveDomain(null)}>我的认知</button><button className={evolutionScope !== undefined ? 'nav-active' : ''} type="button" onClick={() => setEvolutionScope(null)}>认知演化</button></nav><button className="logout-button" type="button" disabled={logoutBusy} onClick={() => void logout()}>{logoutBusy ? '退出中…' : '退出登录'}</button></div></div></header>
    <main className="home">
      <div className="home-heading"><div>{activeDomain && <button type="button" className="text-button domain-back" onClick={() => setActiveDomain(null)}>← 返回全部领域</button>}<span className="home-kicker">{activeDomain ? '我的认知 / 领域' : '此刻 · 我的认知'}</span><h1>{activeDomain ? activeDomain.name : '我的认知'}</h1>{!activeDomain && <p>从你已经形成的理解出发，继续探索。</p>}</div>{activeDomain ? <button type="button" className="domain-evolution-button" onClick={() => setEvolutionScope(activeDomain)}>领域演化 <span aria-hidden="true">↗</span></button> : <button className="primary-button" type="button" onClick={openFlow}>＋ 沉淀一段对话</button>}</div>
      {activeDomain ? <div className="domain-page"><section className="current-understanding"><div className="current-understanding-label">当前理解</div><div><p className={domainSummaryExpanded ? 'understanding-text' : 'understanding-text collapsed'}>{activeDomain.cognitiveSummary || activeDomain.description || '这个领域尚无认知摘要。'}</p>{activeDomain.cognitiveSummary && <button className="understanding-expand" type="button" onClick={() => setDomainSummaryExpanded(value => !value)}>{domainSummaryExpanded ? '收起完整理解 ↑' : '展开完整理解 ↓'}</button>}</div></section>{domainLoading ? <p className="domain-empty">正在读取领域知识…</p> : domainDetailError ? <p className="notice error-text" role="alert">{domainDetailError} <button type="button" onClick={() => void openDomain(activeDomain, selectedKnowledgeId)}>重试</button></p> : domainCognition && <DomainCognitionView data={domainCognition} selectedKnowledgeId={selectedKnowledgeId} onSelectKnowledge={setSelectedKnowledgeId} />}</div> : <>
        {error && screen === 'home' && <p className="notice error-text" role="alert">{error}</p>}
        {domainError && <p className="notice error-text">{domainError} <button type="button" onClick={() => void loadDomains()}>重试</button></p>}
        {!domainError && domains.length === 0 && <div className="empty-home"><h2>还没有认知领域</h2><p>沉淀一段对话后，审核 AI 建议的领域，你的认知会从这里开始积累。</p><button type="button" onClick={openFlow}>开始第一次沉淀 →</button></div>}
        {domains.map(domain => <section className="domain-row" key={domain.id}><button className="domain-name" type="button" onClick={() => void openDomain(domain)}>{domain.name} <span>↗</span></button><div><p>{domain.cognitiveSummary || domain.description || '这个领域尚无认知摘要。'}</p><div className="recent-knowledge">{(domain.recentKnowledge ?? []).map(item => <button type="button" key={item.id} onClick={() => void openDomain(domain, item.id)}>{item.title}</button>)}</div></div></section>)}
      </>}
    </main>

    {evolutionScope !== undefined && <EvolutionOverlay domain={evolutionScope} onClose={() => setEvolutionScope(undefined)} />}
    {screen !== 'home' && <div className={`overlay ${screen === 'import' ? '' : 'overlay-full'}`}>
      <div className="flow-window" role="dialog" aria-modal="true" aria-label="沉淀对话">
        <div className="flow-header"><strong>MindFlow · 沉淀对话</strong><div className="flow-steps"><span className={screen === 'import' ? 'on' : ''}>导入</span><span className={screen === 'draft' ? 'on' : ''}>审核草稿</span><span className={screen === 'plan' ? 'on' : ''}>审核最终变更</span></div><button type="button" className="close-button" onClick={closeFlow} aria-label="关闭">×</button></div>
        {screen === 'import' && <div className="import-body"><h2>导入对话</h2><p className="muted">选择一种方式开始。导入不会直接修改知识库。</p><div className="mode-select"><button className={mode === 'text' ? 'selected' : ''} type="button" onClick={() => { setMode('text'); setError('') }}>粘贴文本</button><button className={mode === 'pdf' ? 'selected' : ''} type="button" onClick={() => { setMode('pdf'); setError('') }}>上传 PDF</button></div>{mode === 'text' ? <textarea className="import-text" value={text} onChange={event => setText(event.target.value)} placeholder="在这里粘贴你与 AI 的对话……" disabled={Boolean(busy)} /> : <div className="upload-box"><input type="file" accept=".pdf,application/pdf" onChange={event => setFile(event.target.files?.[0] ?? null)} disabled={Boolean(busy)} /><p>支持从 ChatGPT 分享页打印、含可提取文字的 PDF。最大 15 MB、200 页。</p></div>}{error && <p className="notice error-text" role="alert">{error}</p>}<div className="import-footer"><span>{busy === 'import' ? '正在导入对话…' : busy === 'draft' ? 'AI 正在整理草稿…' : '导入后仍需两次审核'}</span><button className="primary-button" type="button" onClick={() => void importConversation()} disabled={Boolean(busy)}>{busy ? '请稍候…' : '导入并整理 →'}</button></div></div>}

        {screen === 'draft' && draft && <><div className="review-layout"><div className="review-main"><h2>审核提取内容</h2><p className="muted">第一次 AI 从对话中提取的草稿，可在此修改。</p><section className="review-section"><h3>对话概览</h3><label>标题<input value={draft.title} onChange={event => editDraft({ ...draft, title: event.target.value })} /></label><label>摘要<textarea value={draft.summary} onChange={event => editDraft({ ...draft, summary: event.target.value })} /></label></section><section className="review-section"><h3>知识 <small>{draft.knowledge_preview.length} 条</small></h3>{draft.knowledge_preview.map((item, index) => <div className="draft-item" key={item.temp_id}><div className="item-heading"><strong>知识 {index + 1}</strong><button type="button" onClick={() => deleteKnowledge(item.temp_id)}>删除</button></div><input aria-label="知识标题" value={item.title} onChange={event => editDraft({ ...draft, knowledge_preview: draft.knowledge_preview.map(k => k.temp_id === item.temp_id ? { ...k, title: event.target.value } : k) })} /><textarea aria-label="知识描述" value={item.description} onChange={event => editDraft({ ...draft, knowledge_preview: draft.knowledge_preview.map(k => k.temp_id === item.temp_id ? { ...k, description: event.target.value } : k) })} /><div className="domain-assign"><span>所属领域</span>{domains.map(domain => <label key={domain.id}><input type="checkbox" checked={item.domain_ids?.includes(domain.id) ?? false} onChange={event => editDraft({ ...draft, knowledge_preview: draft.knowledge_preview.map(k => k.temp_id === item.temp_id ? { ...k, domain_ids: event.target.checked ? [...(k.domain_ids ?? []), domain.id] : (k.domain_ids ?? []).filter(id => id !== domain.id) } : k) })} />{domain.name}</label>)}{draft.domain_preview.filter(domain => domain.id === null).map(domain => <label key={domain.temp_id}><input type="checkbox" checked={item.domain_temp_ids?.includes(domain.temp_id) ?? false} onChange={event => editDraft({ ...draft, knowledge_preview: draft.knowledge_preview.map(k => k.temp_id === item.temp_id ? { ...k, domain_temp_ids: event.target.checked ? [...(k.domain_temp_ids ?? []), domain.temp_id] : (k.domain_temp_ids ?? []).filter(id => id !== domain.temp_id) } : k) })} />{domain.name}（新）</label>)}</div></div>)}</section><section className="review-section"><h3>关系 <small>{draft.relation_preview.length} 条</small></h3>{draft.relation_preview.map((item, index) => <div className="draft-item" key={index}><div className="item-heading"><strong>关系 {index + 1}</strong><button type="button" onClick={() => editDraft({ ...draft, relation_preview: draft.relation_preview.filter((_, i) => i !== index) })}>删除</button></div><textarea aria-label="关系描述" value={item.description} onChange={event => editDraft({ ...draft, relation_preview: draft.relation_preview.map((r, i) => i === index ? { ...r, description: event.target.value } : r) })} /><p className="reference">关联：{item.knowledge_temp_ids.map(id => draft.knowledge_preview.find(k => k.temp_id === id)?.title ?? id).join(' ↔ ')}</p></div>)}</section><section className="review-section"><h3>演化 <small>{draft.evolution_preview.length} 条</small></h3>{draft.evolution_preview.map((item, index) => <div className="draft-item" key={index}><div className="item-heading"><strong>演化 {index + 1}</strong><button type="button" onClick={() => editDraft({ ...draft, evolution_preview: draft.evolution_preview.filter((_, i) => i !== index) })}>删除</button></div><input aria-label="演化标题" value={item.title} onChange={event => editDraft({ ...draft, evolution_preview: draft.evolution_preview.map((e, i) => i === index ? { ...e, title: event.target.value } : e) })} /><textarea aria-label="演化描述" value={item.description} onChange={event => editDraft({ ...draft, evolution_preview: draft.evolution_preview.map((e, i) => i === index ? { ...e, description: event.target.value } : e) })} /><p className="reference">涉及：{item.knowledge_temp_ids.map(id => draft.knowledge_preview.find(k => k.temp_id === id)?.title ?? id).join('、')}</p></div>)}</section><section className="review-section"><h3>领域建议 <small>{draft.domain_preview.length} 条</small></h3>{draft.domain_preview.map(domain => <div className="draft-item" key={domain.temp_id}><div className="item-heading"><strong>{domain.id === null ? '建议新建' : '已有领域'}</strong>{domain.id === null && <button type="button" onClick={() => deleteDomain(domain.temp_id)}>取消建议</button>}</div><input aria-label="领域名称" value={domain.name} disabled={domain.id !== null} onChange={event => editDraft({ ...draft, domain_preview: draft.domain_preview.map(d => d.temp_id === domain.temp_id ? { ...d, name: event.target.value } : d) })} /><textarea aria-label="领域说明" value={domain.description ?? ''} disabled={domain.id !== null} onChange={event => editDraft({ ...draft, domain_preview: draft.domain_preview.map(d => d.temp_id === domain.temp_id ? { ...d, description: event.target.value } : d) })} /></div>)}</section></div><aside className="source-panel"><h3>原始对话</h3><pre>{draft.sourceContent}</pre></aside></div><div className="review-footer"><span>{draftDirty ? '草稿已修改；关闭后在当前页面保留' : '此时不会修改知识库'}</span><div>{error && <span className="footer-error" role="alert">{error}</span>}<button className="primary-button" type="button" disabled={Boolean(busy)} onClick={() => void generatePlan()}>{busy === 'plan' ? '正在生成最终方案…' : '生成最终方案 →'}</button></div></div></>}

        {screen === 'plan' && plan && <>
          <div className="review-layout"><div className="review-main"><h2>审核最终变更</h2><p className="muted">以下是确认后将写入知识库的内容。此页只读。</p>
            {emptyChanges(plan) ? <div className="no-changes"><h3>无需新增认知</h3><p>现有知识库已覆盖本次内容。确认后会将这次草稿标记为已处理。</p></div> : <>
              <ChangeGroup title="知识" items={plan.changes.knowledge ?? []} /><ChangeGroup title="关系" items={plan.changes.relations ?? []} /><ChangeGroup title="演化" items={plan.changes.evolutions ?? []} /><ChangeGroup title="新增领域" items={(plan.changes.domains ?? []).filter(item => item.operation !== 'USE')} /><ChangeGroup title="领域摘要" items={plan.changes.domainSummaries ?? []} />
            </>}
          </div><aside className="source-panel"><h3>最终方案</h3><p>第二次 AI 可能不采纳草稿中的建议，也可能提出新内容。请以左侧变更为准。</p>{plan.changes.domains?.some(item => item.operation === 'USE') && <p>沿用已有领域：{plan.changes.domains.filter(item => item.operation === 'USE').map(item => readable(field(item.after, 'name'))).join('、')}。这不是新增或修改领域。</p>}<p>有效期至：{plan.expiresAt ? plan.expiresAt.replace('T', ' ').slice(0, 19) : '以服务端为准'}</p></aside></div>
          <div className="review-footer"><button type="button" onClick={backToDraft}>{draft ? '← 返回修改草稿' : '← 重新导入对话'}</button><div>{(statusMessage(plan) || error) && <span className="footer-error" role="alert">{error || statusMessage(plan)}</span>}<button className="primary-button" type="button" disabled={Boolean(busy) || !canConfirm(plan) || Boolean(error)} onClick={() => void confirmPlan()}>{busy === 'confirm' ? '正在确认沉淀…' : '确认并沉淀'}</button></div></div>
        </>}

        {screen === 'done' && <div className="done-view"><h2>沉淀完成</h2><p>知识库已更新，首页认知领域已刷新。</p><button className="primary-button" type="button" onClick={() => { setDraft(null); setPlan(null); setText(''); setFile(null); closeFlow() }}>返回我的认知</button></div>}
      </div>
    </div>}
  </div>
}

export default App
