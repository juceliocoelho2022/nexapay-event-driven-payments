# ADR-004 — Observabilidade, SLOs e alertas

**Status:** Aceita

## Contexto
Logs isolados não permitem avaliar adequadamente disponibilidade, latência e comportamento distribuído do fluxo de pagamento.

## Decisão
Combinar métricas, logs e traces e definir SLOs/alertas para sinais que representem impacto real no serviço.

## Alternativas consideradas
- Apenas logs: baixo custo inicial, baixa correlação entre serviços.
- Métricas sem SLO: boa visibilidade técnica, mas sem objetivo operacional explícito.
- Métricas + logs + traces + SLOs: maior custo de instrumentação, melhor diagnóstico e operação.

## Trade-offs
Aceitamos custo de armazenamento, instrumentação e manutenção dos dashboards em troca de menor tempo de diagnóstico e critérios objetivos de confiabilidade.

## Riscos
Alta cardinalidade, alert fatigue e dashboards que não refletem impacto no usuário.

## Consequências
Alertas devem ser acionáveis; SLOs devem orientar priorização; failure drills validam se telemetria e alertas funcionam antes de um incidente real.
