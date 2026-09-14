import { useEffect, useState } from 'react'
import { api } from './api'
import type { Domain, EvolutionItem } from './types'
import './evolution-overlay.css'

const eventLabel: Record<EvolutionItem['eventType'], string> = {
  NEW: '新增', EXPANDED: '扩展', REVISED: '修订', CORRECTED: '纠正',
}

export function EvolutionOverlay({ domain, onClose }: { domain: Domain | null, onClose: () => void }) {
  const [items, setItems] = useState<EvolutionItem[]>([])
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [summaryExpanded, setSummaryExpanded] = useState(false)
  const [domainFilter, setDomainFilter] = useState<number | null>(null)

  async function load(nextPage: number) {
    setLoading(true)
    setError('')
    try {
      const result = await api.evolutions(nextPage, domain?.id)
      setItems(previous => nextPage === 1 ? result.items : [...previous, ...result.items])
      if (nextPage === 1) setSelectedId(result.items[0]?.id ?? null)
      setPage(nextPage)
      setHasMore(result.hasMore)
    } catch (reason) { setError(reason instanceof Error ? reason.message : '加载失败，请重试。') }
    finally { setLoading(false) }
  }

  useEffect(() => { void load(1) }, [domain?.id])

  const selected = items.find(item => item.id === selectedId) ?? items[0]
  const timelineDomains = Array.from(new Map(items.flatMap(item => item.domains.map(value => [value.id, value] as const))).values())
  const timelineDates = Array.from(new Set(items.map(item => item.createdAt?.slice(0, 10) || '时间未知'))).sort()
  const timelineLanes = domainFilter === null ? timelineDomains : timelineDomains.filter(value => value.id === domainFilter)
  const visibleSelected = domainFilter === null || selected?.domains.some(value => value.id === domainFilter) ? selected : items.find(item => item.domains.some(value => value.id === domainFilter))
  const timelineColumns = { gridTemplateColumns: `150px repeat(${Math.max(timelineDates.length, 1)}, minmax(180px, 1fr))` }
  return <div className="overlay overlay-full"><div className="flow-window evolution-window" role="dialog" aria-modal="true" aria-label={domain ? `${domain.name}的领域演化` : '认知演化'}>
    <div className="flow-header"><strong>MindFlow · {domain ? '领域演化' : '认知演化'}</strong><button type="button" className="close-button" onClick={onClose} aria-label="关闭">×</button></div>
    <div className="evolution-heading"><h2>{domain ? `${domain.name} · 领域演化` : '认知演化'}</h2><p>{domain ? '回看这个领域的理解如何形成。' : '沿着时间，看看理解如何一步步改变。'}</p></div>
    {domain && <div className="evolution-current"><strong>现在的理解</strong><p className={summaryExpanded ? '' : 'collapsed'}>{domain.cognitiveSummary || '这个领域尚无认知摘要。'}</p>{domain.cognitiveSummary && <button type="button" onClick={() => setSummaryExpanded(value => !value)}>{summaryExpanded ? '收起' : '展开完整理解'}</button>}</div>}
    {!domain ? <div className="evolution-global">
      <div className="evolution-filters"><button type="button" className={domainFilter === null ? 'selected' : ''} onClick={() => setDomainFilter(null)}>全部领域</button>{timelineDomains.map(value => <button type="button" key={value.id} className={domainFilter === value.id ? 'selected' : ''} onClick={() => setDomainFilter(value.id)}>{value.name}</button>)}</div>
      <div className="evolution-lanes"><div className="evolution-lane evolution-axis" style={timelineColumns}><strong>时间 / 领域</strong>{timelineDates.map(date => <span key={date}>{date}</span>)}</div>
        {timelineLanes.map(value => <div className="evolution-lane" style={timelineColumns} key={value.id}><strong>{value.name}</strong>{timelineDates.map(date => <div className="evolution-lane-cell" key={date}>{items.filter(item => item.createdAt?.slice(0, 10) === date && item.domains.some(link => link.id === value.id)).map(item => <button type="button" key={item.id} className={item.id === selected?.id ? 'evolution-node selected' : 'evolution-node'} onClick={() => setSelectedId(item.id)}><i aria-hidden="true"/><b>{item.title}</b><small>{eventLabel[item.eventType] || item.eventType}</small></button>)}</div>)}</div>)}
        {items.some(item => item.domains.length === 0) && domainFilter === null && <div className="evolution-lane" style={timelineColumns}><strong>尚未归属</strong>{timelineDates.map(date => <div className="evolution-lane-cell" key={date}>{items.filter(item => item.createdAt?.slice(0, 10) === date && item.domains.length === 0).map(item => <button type="button" key={item.id} className={item.id === selected?.id ? 'evolution-node selected' : 'evolution-node'} onClick={() => setSelectedId(item.id)}><i aria-hidden="true"/><b>{item.title}</b><small>{eventLabel[item.eventType] || item.eventType}</small></button>)}</div>)}</div>}
        {!loading && !error && items.length === 0 && <p className="evolution-empty">还没有演化记录。</p>}
      </div>
      {error && <p className="notice error-text" role="alert">{error} <button type="button" onClick={() => void load(page)}>重试</button></p>}{loading && <p className="evolution-empty">正在读取演化记录…</p>}{hasMore && !loading && <button className="evolution-more" type="button" onClick={() => void load(page + 1)}>加载更多</button>}
      <div className="evolution-global-detail" aria-live="polite">{visibleSelected ? <><div><span>{visibleSelected.createdAt?.slice(0, 10)} · {visibleSelected.domains.map(value => value.name).join('、') || '尚未归属'} · {eventLabel[visibleSelected.eventType] || visibleSelected.eventType}</span><h3>{visibleSelected.title}</h3></div><div><strong>这次理解改变了什么</strong><p>{visibleSelected.content}</p></div><div><strong>涉及的知识</strong><p>{visibleSelected.knowledge.map(value => value.title).join('、') || '无关联知识'}</p></div></> : <p className="evolution-empty">选择时间线上的变化，查看具体内容。</p>}</div>
    </div> : <div className="evolution-content"><div className="evolution-list"><h3>形成过程</h3><div className="evolution-track">
      {items.map((item, index) => <div className="evolution-step" key={item.id}>
        {(index === 0 || item.createdAt?.slice(0, 10) !== items[index - 1]?.createdAt?.slice(0, 10)) && <div className="evolution-day">{item.createdAt?.slice(0, 10) || '时间未知'}</div>}
        <button type="button" className={item.id === selected?.id ? 'evolution-entry selected' : 'evolution-entry'} onClick={() => setSelectedId(item.id)}>
          <span className="evolution-date">{domain ? `第 ${index + 1} 步 · ` : ''}{eventLabel[item.eventType] || item.eventType}{!domain && ` · ${item.domains.map(value => value.name).join('、') || '尚未归属领域'}`}</span><strong>{item.title}</strong>
        </button>
      </div>)}
      </div>
      {!loading && !error && items.length === 0 && <p className="evolution-empty">{domain ? '这个领域还没有演化记录。' : '还没有演化记录。'}</p>}
      {error && <p className="notice error-text" role="alert">{error} <button type="button" onClick={() => void load(page)}>重试</button></p>}
      {loading && <p className="evolution-empty">正在读取演化记录…</p>}
      {hasMore && !loading && <button className="evolution-more" type="button" onClick={() => void load(page + 1)}>加载更多</button>}
    </div><aside className="evolution-detail" aria-live="polite">{selected ? <><span>{selected.createdAt?.replace('T', ' ').slice(0, 16)} · {eventLabel[selected.eventType] || selected.eventType}</span><h3>{selected.title}</h3><p>{selected.content}</p><div className="evolution-linked"><strong>涉及知识</strong><p>{selected.knowledge.map(value => value.title).join('、') || '无关联知识'}</p></div></> : <p className="evolution-empty">选择左侧记录，查看具体内容。</p>}</aside></div>}
  </div></div>
}
