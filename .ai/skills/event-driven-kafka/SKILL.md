# Skill — Event-Driven Kafka

## Quando usar

Mudanças em producers, consumers, eventos, Outbox, retry, DLT, idempotência, replay ou tracing Kafka.

## Invariantes

- assumir entrega at-least-once;
- consumer deve suportar redelivery sem duplicar efeito;
- não prometer exactly-once global;
- retry deve ser limitado;
- falha permanente deve ser explicitamente tratada;
- replay de DLT precisa ser controlado;
- alteração de evento/tópico requer análise de compatibilidade;
- propagação de correlation/trace context deve ser mantida.

## Producer

Antes de alterar publicação:

1. verificar se o fluxo usa Transactional Outbox;
2. evitar dual write banco + Kafka;
3. preservar chave/identidade necessária à idempotência;
4. validar observabilidade da publicação.

## Consumer

Antes de alterar consumo:

1. identificar efeito de negócio;
2. verificar idempotência;
3. definir comportamento em retry/redelivery;
4. verificar DLT;
5. validar commit/ack conforme implementação existente;
6. criar teste para duplicidade ou falha quando relevante.

## Testes recomendados

- unitário para regra;
- integração real com Kafka/Testcontainers quando o risco justificar;
- duplicidade/redelivery;
- falha transitória;
- caminho para DLT;
- replay controlado;
- preservação de tracing/correlation.

## Sinais operacionais

Revisar impacto em:

- consumer lag;
- retry count;
- DLT count;
- Outbox backlog;
- taxa de erro;
- spans de send/receive.
