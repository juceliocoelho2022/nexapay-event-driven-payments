# ADR-008 — Cancelamento por transição atômica de estado

**Status:** Aceita

## Contexto

PIX agendado e recorrente são processados por workers concorrentes. Um cancelamento não pode depender de um `SELECT` seguido por `save`, pois a decisão poderia ficar obsoleta antes da escrita.

## Decisão

Usar conditional updates no PostgreSQL.

### Pagamento

```sql
UPDATE payments
SET status = 'CANCELLED', ...
WHERE id = :id
  AND status = 'SCHEDULED';
```

### Recorrência

```sql
UPDATE recurring_pix_schedules
SET status = 'CANCELLED', ...
WHERE id = :id
  AND status = 'ACTIVE';
```

`rowsUpdated == 1` define o vencedor.

## Cancelamento de recorrência

Após obter o claim da regra, a mesma transação cancela pagamentos da regra ainda em `SCHEDULED`.

Pagamentos que já chegaram a `PENDING` não são revertidos.

## Auditoria

Cancelamentos vencedores geram registro imutável em `cancellation_audit` com actor do JWT, motivo e instante.

A auditoria fica na mesma transação local da mudança de estado.

## Alternativas rejeitadas

### Lock distribuído

Desnecessário: o estado protegido já está no PostgreSQL.

### SELECT seguido de save

Rejeitado por janela de race condition.

### Evento Kafka de cancelamento no v1

Não necessário porque pagamentos canceláveis ainda não entraram no fluxo `PaymentCreated`.

Se cancelamento pós-publicação for introduzido no futuro, será necessário um contrato de compensação explícito.

## Consequências

- cancelamento e execução são linearizáveis pela linha do banco;
- sem infraestrutura adicional;
- comportamento concorrente pode ser provado por Testcontainers;
- não há rollback financeiro de pagamentos já processados.
