# Workflow — Bugfix

## Princípio

Não corrigir sintoma sem evidência da causa.

## Fluxo

```text
reproduzir
 -> coletar evidência
 -> hipótese
 -> teste que falha
 -> correção mínima
 -> testes
 -> observabilidade
 -> PR
```

## Passos

1. Reproduzir o problema.
2. Capturar evidência disponível: erro, log, métrica, trace, mensagem Kafka ou estado persistido.
3. Delimitar o componente onde o comportamento diverge do esperado.
4. Criar teste de regressão que falha pelo motivo correto.
5. Aplicar correção mínima.
6. Executar testes do módulo e integrações afetadas.
7. Confirmar que idempotência, retry e contratos não regrediram.
8. Quando for incidente distribuído, correlacionar logs + metrics + traces.
9. Documentar causa raiz e prevenção no PR.

## Não fazer

- adicionar retry infinito;
- engolir exception;
- desabilitar teste para deixar CI verde;
- alterar contrato público sem explicação;
- introduzir nova tecnologia como atalho para um bug local;
- afirmar causa raiz sem evidência.
