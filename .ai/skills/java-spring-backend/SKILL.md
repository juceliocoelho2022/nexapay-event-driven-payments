# Skill — Java Spring Backend

## Quando usar

Mudanças em controllers, services, repositories, DTOs, validação, persistência, segurança ou configuração Spring no NexaPay.

## Regras

- Java 21 e Spring Boot 3.5.x.
- Preferir constructor injection.
- Controller coordena HTTP; não concentra regra de negócio.
- DTOs representam contratos externos.
- Bean Validation deve proteger entradas quando aplicável.
- Usar `BigDecimal` para valores monetários.
- Demarcação transacional deve ser explícita onde consistência local é necessária.
- Não expor entity JPA diretamente por conveniência.
- Erros devem preservar o padrão já utilizado pelo serviço.
- Mudança de contrato exige SPEC/testes atualizados.
- Não adicionar dependência sem justificar requisito, risco resolvido e custo.

## Fluxo

1. localizar a SPEC;
2. ler código e testes da capacidade;
3. identificar invariantes;
4. implementar menor mudança possível;
5. criar/ajustar testes;
6. executar testes do módulo;
7. executar `mvn -B clean verify` quando o ambiente permitir;
8. revisar impacto em logs, métricas e traces.

## Checklist de revisão

- [ ] regra no layer correto;
- [ ] transação consistente;
- [ ] precisão monetária preservada;
- [ ] validação de entrada presente;
- [ ] autorização/autenticação não regrediu;
- [ ] exceptions não vazam detalhe sensível;
- [ ] testes cobrem happy path e falhas relevantes;
- [ ] nenhuma API foi inventada.
