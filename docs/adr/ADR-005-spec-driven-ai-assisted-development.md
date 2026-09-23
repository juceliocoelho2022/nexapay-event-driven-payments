# ADR-005 — Spec-Driven e AI-Assisted Development

**Status:** Aceita

## Contexto

O NexaPay já possui múltiplos serviços, contratos HTTP, eventos Kafka, políticas de resiliência e requisitos de observabilidade. Nesse cenário, uma alteração aparentemente pequena pode gerar efeitos em várias fronteiras.

O uso de assistentes de IA acelera implementação e revisão, mas aumenta o risco de mudanças baseadas em contexto incompleto, APIs inventadas, regressões de contrato e adição desnecessária de tecnologia.

## Decisão

Adotar uma abordagem de **Spec-Driven Development (SDD)** para mudanças comportamentais relevantes e usar IA como ferramenta de implementação/revisão subordinada às especificações, ADRs e evidências do repositório.

Cada mudança relevante deve partir de:

1. intenção/problema;
2. requisitos e invariantes;
3. critérios de aceite;
4. análise de impacto;
5. plano de testes;
6. implementação;
7. validação mecânica;
8. evidência operacional quando aplicável.

O arquivo `AGENTS.md` será a referência de guardrails para agentes de IA e contribuidores.

Skills reutilizáveis ficarão em `.ai/skills/` e workflows em `.ai/workflows/`.

## Consequências positivas

- menor chance de implementação divergente do requisito;
- contexto mais consistente entre sessões/agentes;
- decisões e trade-offs ficam auditáveis;
- revisão de PR passa a comparar código com critérios de aceite;
- facilita demonstrar autonomia de engenharia no portfólio.

## Custos e trade-offs

- existe custo adicional de documentação;
- specs desatualizadas geram falsa segurança;
- nem toda mudança pequena exige uma SPEC nova;
- IA continua exigindo validação por testes, CI e revisão humana.

## Guardrails

- IA não é fonte de verdade sobre o sistema;
- código existente, testes, ADRs e specs têm precedência;
- nenhuma mudança arquitetural deve ser introduzida apenas porque uma ferramenta sugeriu;
- loops automáticos devem ter critério de parada;
- mudanças em segurança, dinheiro, idempotência ou contratos exigem revisão explícita.

## Alternativas consideradas

### Prompt ad hoc por tarefa

Rejeitada como estratégia principal por perder contexto e regras entre sessões.

### Agente autônomo sem SPEC

Rejeitada para mudanças relevantes porque reduz rastreabilidade e aumenta risco de alterações não intencionais.

### Documentação sem validação automática

Insuficiente. A SPEC orienta; testes, build, lint/quality gates, CI e observabilidade produzem evidência.
