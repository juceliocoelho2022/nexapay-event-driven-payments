# ADR-011 — Fraud Review Queue com lease no PostgreSQL

**Status:** Aceita

## Contexto

A Sprint 17 permite decisão humana sobre pagamentos em `REVIEW`, mas não existe coordenação operacional entre analistas.

Sem ownership, dois analistas podem trabalhar no mesmo caso e apenas descobrir o conflito no momento da decisão final.

## Decisão

Criar `fraud_review_cases` e usar um **lease temporal** para ownership.

O claim é obtido por conditional update no PostgreSQL.

Duração padrão: 15 minutos.

## Motivos

- evita trabalho duplicado entre analistas;
- não introduz Redis lock ou scheduler externo;
- permite recuperação automática se um analista abandonar o caso;
- ownership e estado da revisão ficam na mesma fronteira de consistência.

## Criação do caso

Quando `FraudDecisionMade(REVIEW)` transforma o pagamento em `REVIEW`, um caso OPEN é criado na mesma transação.

Se a criação do caso falhar, a transição e a reserva idempotente do evento também são revertidas.

## Decisão manual

A resolução exige lease válido do mesmo reviewer.

A transação:

1. altera Payment `REVIEW -> COMPLETED/REJECTED`;
2. resolve o `fraud_review_case`;
3. grava auditoria imutável.

Se qualquer etapa falhar, tudo sofre rollback.

## Alternativas

### Lock distribuído com Redis

Não adotado: o PostgreSQL já contém o estado protegido.

### Claim sem expiração

Não adotado: casos poderiam ficar presos indefinidamente.

### Fila externa

Pode ser avaliada se volume, SLA ou roteamento de casos justificarem infraestrutura adicional.

## Consequências

- polling/listagem da fila continua simples;
- lease requer semântica clara de relógio e expiração;
- analistas precisam claim antes de decidir;
- a fila passa a ser parte explícita do workflow antifraude.
