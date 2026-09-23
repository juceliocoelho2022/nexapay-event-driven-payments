# SPEC — PIX Payment v1

**Status:** baseline documentada da capacidade existente  
**Serviço principal:** Payment Service  
**Integração assíncrona principal:** Kafka → Fraud Service

## 1. Intenção

Permitir a criação e consulta de pagamentos PIX com proteção contra duplicidade, persistência transacional, publicação confiável de eventos e rastreabilidade operacional.

## 2. Contratos externos conhecidos

```http
POST /api/v1/payments/pix
GET  /api/v1/payments/{id}
```

A criação de PIX utiliza o header `Idempotency-Key`.

## 3. Evento de domínio/integracão

Evento publicado pelo fluxo atual:

```text
nexapay.payment.created.v1
```

O Fraud Service consome esse evento para análise assíncrona.

## 4. Requisitos funcionais

- criar um pagamento PIX válido;
- consultar pagamento existente por identificador;
- impedir efeito duplicado quando a mesma operação é reenviada conforme o contrato de idempotência;
- persistir a intenção de publicação usando Transactional Outbox;
- publicar o evento de pagamento criado;
- permitir processamento downstream assíncrono;
- manter informações suficientes para diagnóstico do fluxo distribuído.

## 5. Invariantes

- valores monetários não podem perder precisão;
- a mesma operação idempotente não pode criar dois efeitos financeiros;
- persistência de domínio e registro de Outbox não devem formar um dual write independente;
- redelivery Kafka faz parte do modelo normal de falha;
- consumidores precisam ser seguros para retry/replay;
- falha downstream não deve apagar evidência da criação já persistida;
- o sistema não promete exactly-once global.

## 6. Modelo de falha esperado

### Requisição HTTP repetida
Resultado esperado: proteção por idempotência, sem criação de novo efeito equivalente.

### Kafka indisponível temporariamente
Resultado esperado: evento permanece recuperável pelo mecanismo de Outbox/retry; a falha deve ser observável.

### Consumer falha temporariamente
Resultado esperado: retry limitado de acordo com a política do serviço.

### Consumer excede política de retry
Resultado esperado: isolamento em DLT quando previsto pelo fluxo, com replay operacional controlado.

## 7. Observabilidade

A operação deve permitir correlação entre:

```text
API Gateway
  -> Payment Service
  -> Transactional Outbox
  -> Kafka
  -> Fraud Service
```

Evidências esperadas:

- correlationId/traceId;
- spans HTTP;
- span de publicação Kafka;
- span de consumo Kafka;
- logs estruturados;
- métricas de erro/retry/DLT/outbox quando aplicáveis.

## 8. Critérios de aceite

- [ ] criação PIX válida retorna conforme o contrato existente;
- [ ] consulta recupera o pagamento persistido;
- [ ] envio duplicado com a mesma chave não duplica o efeito de negócio;
- [ ] pagamento e Outbox permanecem consistentes no fluxo transacional;
- [ ] evento `nexapay.payment.created.v1` é publicável e consumível;
- [ ] falha Kafka não causa perda silenciosa da intenção de publicação;
- [ ] retry não é infinito;
- [ ] cenários relevantes possuem testes automatizados;
- [ ] CI permanece verde;
- [ ] fluxo é diagnosticável por logs, métricas e traces.

## 9. Protocolo para evolução desta SPEC

Qualquer alteração em endpoint, idempotência, evento, semântica de entrega ou estado do pagamento deve:

1. atualizar esta SPEC;
2. verificar necessidade de ADR;
3. avaliar compatibilidade backward;
4. definir testes antes da implementação;
5. registrar impacto operacional e de observabilidade.
