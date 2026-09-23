# Workflow — Feature Development

## Entrada

Uma feature, melhoria ou alteração de comportamento.

## Loop controlado

```text
SPEC
 -> análise de impacto
 -> plano
 -> implementação
 -> testes
 -> review contra SPEC
 -> correção (se necessária)
 -> quality gates
 -> PR
```

Máximo recomendado: três ciclos automáticos de correção antes de exigir nova análise do problema.

## Passos

### 1. Contexto
- ler `AGENTS.md`;
- localizar/gerar SPEC;
- ler ADRs relevantes;
- localizar código e testes existentes.

### 2. Análise
Registrar:
- serviços afetados;
- endpoints/eventos afetados;
- persistência;
- segurança;
- idempotência;
- observabilidade;
- compatibilidade.

### 3. Critérios de aceite
Converter requisito em condições verificáveis antes de implementar.

### 4. Implementação
Fazer o menor slice vertical coerente com a arquitetura atual.

### 5. Testes
Priorizar testes pelo risco:
- regra;
- contrato HTTP;
- persistência;
- Kafka;
- idempotência;
- falha parcial.

### 6. Review contra SPEC
Perguntas:
- todos os critérios foram atendidos?
- alguma invariável foi quebrada?
- foi adicionada complexidade não solicitada?
- contratos mudaram?
- observabilidade continua suficiente?

### 7. Quality gates
Quando aplicável:
- testes do módulo;
- `mvn -B clean verify`;
- Docker/Testcontainers;
- CI;
- smoke test.

### 8. PR
O PR deve explicar:
- problema;
- decisão;
- trade-offs;
- testes;
- evidência;
- riscos restantes.
