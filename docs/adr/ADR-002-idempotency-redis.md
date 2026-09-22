# ADR-002 — Idempotência com Redis

**Status:** Aceita

## Contexto
Retries de clientes, gateways e consumidores podem repetir uma operação de pagamento. Em domínio financeiro, duplicidade pode gerar efeitos críticos.

## Decisão
Usar uma idempotency key por operação e Redis como armazenamento de baixa latência para detectar/reutilizar resultados de requisições já processadas.

## Alternativas consideradas
- Apenas constraint no PostgreSQL: forte consistência, porém concentra contenção no banco.
- Memória local: baixa latência, mas não funciona de forma consistente entre réplicas.
- Redis: compartilhado e rápido, com custo operacional e política de expiração.

## Trade-offs
Ganhamos baixa latência e coordenação entre instâncias, assumindo dependência adicional e definição cuidadosa de TTL.

## Riscos
TTL curto pode permitir reprocessamento; TTL excessivo aumenta uso de memória; indisponibilidade do Redis exige política explícita de fallback/fail-safe.

## Consequências
A chave deve representar semanticamente a operação, e métricas devem distinguir requisições novas de deduplicadas.
