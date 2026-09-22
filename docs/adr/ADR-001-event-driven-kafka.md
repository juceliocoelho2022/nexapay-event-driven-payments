# ADR-001 — Kafka e arquitetura orientada a eventos

**Status:** Aceita

## Contexto
Pagamentos envolvem etapas independentes, integrações e picos de carga. Acoplamento síncrono entre todos os serviços aumenta propagação de falhas e reduz autonomia.

## Decisão
Usar Apache Kafka como backbone de eventos para fluxos assíncronos relevantes, mantendo APIs REST onde resposta síncrona é necessária.

## Alternativas consideradas
- REST síncrono entre todos os serviços: simples, porém aumenta acoplamento temporal.
- Fila tradicional: adequada a comandos, mas menos conveniente para retenção e múltiplos consumidores.
- Kafka: maior complexidade operacional, compensada por desacoplamento, replay e escalabilidade.

## Trade-offs
Aceitamos consistência eventual, operação do broker e necessidade de contratos de eventos em troca de desacoplamento e maior resiliência.

## Riscos
Eventos duplicados, processamento fora de ordem, consumer lag e evolução incompatível de schemas.

## Consequências
Consumidores devem ser idempotentes; observabilidade deve acompanhar lag/erros; falhas permanentes precisam de estratégia de DLT.
