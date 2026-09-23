# ADR-006 — Scheduled PIX com claim atômico no PostgreSQL

**Status:** Aceita

## Contexto

O scheduler do Payment Service pode executar em mais de uma instância. Se duas instâncias selecionarem o mesmo pagamento `SCHEDULED`, ambas poderiam criar um evento na Outbox e iniciar processamento duplicado.

O problema é uma disputa de execução, não uma necessidade de coordenação global entre todos os serviços.

## Decisão

Usar o PostgreSQL como mecanismo de arbitragem por meio de uma atualização condicional atômica:

```sql
UPDATE payments
SET status = 'PENDING',
    executed_at = :now
WHERE id = :id
  AND status = 'SCHEDULED'
  AND scheduled_at <= :now;
```

Somente a instância que atualizar uma linha é autorizada a criar a `PaymentCreated` na Transactional Outbox.

A mudança de estado e a criação da Outbox ocorrem na mesma transação local.

## Motivos

- reutiliza uma dependência já existente;
- reduz infraestrutura adicional;
- mantém a arbitragem próxima ao estado protegido;
- funciona com múltiplas instâncias;
- permite raciocínio simples: `rowsUpdated == 1` significa ownership da execução.

## Alternativas consideradas

### Lock distribuído com Redis

Não adotado neste momento.

Embora possível, adicionaria dependência operacional e uma segunda fonte de coordenação para um problema que o banco já consegue resolver atomicamente.

### SELECT FOR UPDATE

É adequado para alguns cenários, mas mantém locks de linha enquanto a transação está aberta. Para o primeiro slice, o conditional update deixa a seção crítica pequena.

### Quartz ou scheduler externo

Não adotado no v1 porque o requisito atual é simples e o Spring Scheduling já está presente.

Pode ser reavaliado se surgirem calendários complexos, grande volume, misfire policies ou administração distribuída de jobs.

## Consequências

### Positivas

- sem lock distribuído adicional;
- proteção contra execução duplicada entre instâncias;
- integração direta com Transactional Outbox;
- estratégia testável com winner/loser.

### Negativas

- o scheduler ainda faz polling;
- lotes muito grandes poderão exigir estratégia de claim em batch;
- precisão e throughput dependem da frequência de polling e do banco.

## Evolução

Se volume ou concorrência crescerem, avaliar:

- claim em lote;
- `FOR UPDATE SKIP LOCKED`;
- particionamento de agenda;
- scheduler dedicado.

A evolução deve ser baseada em métrica de throughput/lag, não apenas em antecipação de escala.
