import type { DomainCognition } from './types'

type Props = {
  data: DomainCognition
  selectedKnowledgeId: number | null
  onSelectKnowledge: (id: number) => void
}

export function DomainCognitionView({ data, selectedKnowledgeId, onSelectKnowledge }: Props) {
  const selected = data.knowledge.find(item => item.id === selectedKnowledgeId) ?? data.knowledge[0]
  const related = selected ? data.relations.filter(item => item.knowledge.some(link => link.id === selected.id)) : []

  return <section className="domain-explore" aria-label="知识与关系">
    <div className="domain-explore-heading"><h2>知识与关系</h2><span>选择知识，查看内容与联系 · {data.knowledge.length} 条知识</span></div>
    {data.knowledge.length === 0 ? <p className="domain-empty">这个领域还没有知识。沉淀对话并审核后，相关内容会出现在这里。</p> :
      <div className="domain-explore-grid">
        <div className="knowledge-list" aria-label="领域知识">
          {data.knowledge.map((item, index) => <button type="button" className={item.id === selected?.id ? 'knowledge-row selected' : 'knowledge-row'} key={item.id} onClick={() => onSelectKnowledge(item.id)}>
            <span className="knowledge-index">{String(index + 1).padStart(2, '0')}</span><span className="knowledge-row-content"><strong>{item.title}</strong><small>{item.description}</small></span><span aria-hidden="true">↗</span>
          </button>)}
        </div>
        <div className="knowledge-detail">
          <span className="section-kicker">正在查看</span>
          <h3>{selected.title}</h3>
          <p className="knowledge-description">{selected.description}</p>
          <div className="related-heading">与它相连的理解 <span>{related.length}</span></div>
          {related.length === 0 ? <p className="no-relations">暂时没有关联关系。</p> : related.map(relation => <div className="relation-row" key={relation.id}>
            <p>{relation.description}</p>
            <div className="relation-links">{relation.knowledge.filter(link => link.id !== selected.id).map(link => link.inCurrentDomain ?
              <button type="button" key={link.id} onClick={() => onSelectKnowledge(link.id)}>{link.title} ↗</button> :
              <span key={link.id}>{link.title} · 其他领域</span>)}</div>
          </div>)}
        </div>
      </div>}
  </section>
}
