# ADR-009 — Fraud Decision via Outbox e máquina de estados no Payment

**Status:** Aceita

## Contexto

O Fraud Service já consome `PaymentCreated` e persiste a análise, mas a decisão não retorna ao Payment Service. Isso deixa pagamentos indefinidamente em `PENDING`.

Publicar Kafka diretamente após salvar a decisão criaria dual write.

## Decisão

### Fraud Service

Persistir:

1. `FraudDecision`;
2. `FraudDecisionMade` na Transactional Outbox;

na mesma transação PostgreSQL.

Um publisher separado envia a Outbox para:

```text
nexapay.fraud.decision-made.v1
```

### Payment Service

Consumir o evento e aplicar uma máquina de estados explícita:

```text
PENDING + APPROVED -> COMPLETED
PENDING + REVIEW   -> REVIEW
PENDING + BLOCKED  -> REJECTED
```

A reserva idempotente do evento e a mudança do pagamento ficam na mesma transação.

## Motivos

- elimina dual write no Fraud Service;
- mantém semântica at-least-once explícita;
- transforma o estado do pagamento por regras verificáveis;
- permite replay seguro;
- preserva rastreabilidade e métricas.

## Estados não suportados

Uma decisão de fraude para um pagamento fora de `PENDING` não é descartada silenciosamente.

O processamento falha e fica sujeito à política de retry/DLT, pois indica inconsistência ou evento fora de ordem.

## REVIEW

`REVIEW` é estado intermediário explícito e não é convertido em `REJECTED`.

Uma futura Sprint pode introduzir decisão manual:

```text
REVIEW -> COMPLETED
REVIEW -> REJECTED
```

## Consequências

### Positivas

- ciclo assíncrono fechado;
- consistência eventual observável;
- idempotência nos dois sentidos;
- nenhum exactly-once global é reivindicado;
- base para workflow de revisão manual.

### Custos

- nova Outbox no Fraud Service;
- novo consumer no Payment Service;
- mais uma tabela de deduplicação;
- política de erro para eventos fora de estado.
