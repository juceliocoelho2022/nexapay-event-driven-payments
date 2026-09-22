# ADR-003 — Retry, Circuit Breaker e DLT

**Status:** Aceita

## Contexto
Dependências podem falhar de forma transitória ou persistente. Retry indiscriminado pode amplificar incidentes e gerar retry storms.

## Decisão
Aplicar retry somente a falhas potencialmente transitórias, Circuit Breaker para interromper chamadas a dependências degradadas e DLT para eventos que excederem a política de processamento.

## Alternativas consideradas
- Retry infinito: rejeitado por risco de sobrecarga e bloqueio.
- Falha imediata: simples, porém perde recuperação automática de falhas transitórias.
- Retry limitado + Circuit Breaker + DLT: maior configuração, mas comportamento operacional explícito.

## Trade-offs
A solução aumenta componentes e cenários de teste, em troca de isolamento de falhas e recuperação controlada.

## Riscos
Configuração agressiva pode abrir circuitos prematuramente; backoff inadequado pode pressionar dependências; DLT sem processo de replay vira armazenamento de falhas.

## Consequências
Retries precisam de backoff e limites; mensagens em DLT devem ser observáveis, investigáveis e passíveis de replay seguro.
