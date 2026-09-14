export type KnowledgePreview = {
  temp_id: string
  title: string
  description: string
  domain_ids: number[]
  domain_temp_ids: string[]
}

export type RelationPreview = {
  description: string
  knowledge_temp_ids: string[]
}

export type EvolutionPreview = {
  title: string
  description: string
  knowledge_temp_ids: string[]
}

export type DomainPreview = {
  id: number | null
  temp_id: string
  name: string
  description: string
}

export type Draft = {
  title: string
  summary: string
  sourceContent: string
  conversation_id?: number | null
  request_id: string
  knowledge_preview: KnowledgePreview[]
  relation_preview: RelationPreview[]
  evolution_preview: EvolutionPreview[]
  domain_preview: DomainPreview[]
}

export type Domain = {
  id: number
  name: string
  description?: string | null
  cognitiveSummary?: string | null
  recentKnowledge?: { id: number, title: string }[]
}

export type DomainCognition = {
  domain: Domain
  knowledge: { id: number, title: string, description: string, domainIds: number[], updatedAt: string | null }[]
  relations: { id: number, description: string, knowledge: { id: number, title: string, inCurrentDomain: boolean }[] }[]
}

export type EvolutionItem = {
  id: number
  title: string
  content: string
  eventType: 'NEW' | 'EXPANDED' | 'REVISED' | 'CORRECTED'
  createdAt: string
  stepOrder: number | null
  knowledge: { id: number, title: string }[]
  domains: { id: number, name: string }[]
}

export type EvolutionPage = { items: EvolutionItem[], page: number, size: number, hasMore: boolean }

export type PlanChange = {
  operation?: 'CREATE' | 'UPDATE' | 'USE'
  ref?: string
  before?: unknown
  after?: unknown
  domainName?: string
  domainRef?: string
}

export type PlanChanges = {
  knowledge: PlanChange[]
  relations: PlanChange[]
  evolutions: PlanChange[]
  domains: PlanChange[]
  domainSummaries: PlanChange[]
}

export type Plan = {
  planId: number | string
  status: 'READY' | 'EXPIRED' | 'STALE' | 'APPLIED'
  expiresAt: string
  changes: PlanChanges
}
