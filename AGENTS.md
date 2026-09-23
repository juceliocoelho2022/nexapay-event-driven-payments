# NexaPay — Agent Engineering Guide

Este arquivo define o contexto operacional para assistentes de IA, agentes de código e contribuidores humanos que alterem o NexaPay.

## 1. Objetivo

Evoluir o NexaPay com mudanças pequenas, verificáveis e rastreáveis, preservando contratos, invariantes financeiros, semântica de eventos e capacidade de diagnóstico em produção.

## 2. Baseline técnico

- Java 21
- Spring Boot 3.5.x
- Maven multi-module
- PostgreSQL + Flyway
- Apache Kafka
- Transactional Outbox
- Redis para idempotência quando aplicável
- Spring Security + JWT
- JUnit 5, Mockito, MockMvc e Testcontainers
- OpenTelemetry, Prometheus, Grafana, Loki e Tempo
- Docker / Docker Compose
- GitHub Actions

## 3. Invariantes que não podem ser quebradas silenciosamente

1. Operações monetárias usam `BigDecimal`; não usar `float` ou `double` para valores financeiros.
2. O fluxo de pagamento deve continuar idempotente diante de retries e requisições duplicadas.
3. Alteração de domínio + intenção de publicação de evento deve preservar o padrão Transactional Outbox onde ele já é aplicado.
4. Kafka é tratado como entrega at-least-once. Não declarar exactly-once global sem evidência arquitetural.
5. Consumidores devem tolerar redelivery/replay sem duplicar efeitos de negócio.
6. Retry deve ser limitado e observável. Falhas permanentes devem seguir para DLT quando esse for o contrato do fluxo.
7. Mudanças de contratos HTTP, eventos, nomes de tópico ou schema exigem atualização da SPEC e avaliação explícita de compatibilidade.
8. Controllers devem permanecer finos; regras de negócio pertencem à camada de aplicação/domínio.
9. Entidades de persistência não devem virar contrato externo da API por conveniência.
10. correlationId/trace context deve ser preservado nas fronteiras relevantes para diagnóstico.
11. Nunca versionar credenciais, tokens, secrets ou dados sensíveis.

## 4. Processo obrigatório para mudanças

Antes de escrever código:

1. Ler a SPEC da capacidade afetada.
2. Ler ADRs e `docs/engineering-decisions.md`.
3. Mapear serviços, contratos HTTP/eventos, persistência e observabilidade impactados.
4. Registrar premissas e trade-offs.
5. Definir critérios de aceite e estratégia de teste.
6. Implementar o menor slice vertical que satisfaça a SPEC.
7. Executar testes e build.
8. Revisar logs, métricas e traces afetados.
9. Atualizar documentação quando a mudança alterar comportamento ou decisão arquitetural.

## 5. Definition of Done

Uma mudança só está concluída quando, quando aplicável:

- critérios de aceite estão cobertos;
- testes unitários e de integração relevantes passam;
- `mvn -B clean verify` passa no ambiente adequado;
- contratos permanecem compatíveis ou a incompatibilidade está documentada;
- falhas e retries são observáveis;
- não há regressão de idempotência;
- não há novos secrets no repositório;
- documentação/ADR/SPEC foram atualizadas;
- CI fica verde.

## 6. Regras para IA

Assistentes e agentes devem:

- preferir evidência do repositório a suposições;
- não inventar endpoints, tópicos, campos ou dependências;
- explicar trade-offs de mudanças arquiteturais;
- não adicionar tecnologia sem requisito ou risco que a justifique;
- produzir testes junto com mudanças comportamentais;
- interromper uma refatoração ampla se uma alteração menor resolver o problema;
- deixar explícito quando uma validação depende de Docker, Kafka, PostgreSQL ou outro runtime externo.

## 7. Fontes de contexto

Prioridade de leitura:

1. SPEC da feature
2. `docs/adr/`
3. `docs/engineering-decisions.md`
4. código e testes existentes
5. `README.md`
6. workflows de CI
