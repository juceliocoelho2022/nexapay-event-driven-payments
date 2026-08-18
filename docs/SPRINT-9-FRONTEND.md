# Sprint 9 — Frontend React

A Sprint 9 adiciona a interface web do NexaPay em React + TypeScript, consumindo o backend prioritariamente pelo API Gateway em `http://localhost:8080`.

## Stack

- React 19
- TypeScript
- Vite 8
- React Router
- Fetch API
- CSS responsivo

## Funcionalidades entregues

- login e cadastro de usuário;
- sessão autenticada com JWT em `sessionStorage`;
- dashboard protegido;
- criação, consulta, crédito e débito de contas;
- criação de pagamentos PIX com `Idempotency-Key`;
- consulta de pagamentos por ID;
- consulta paginada do Ledger por `accountId`;
- consulta antifraude por `paymentId` quando o usuário possui `FRAUD_READ`;
- tela de perfil com roles e permissions;
- navegação protegida por autenticação;
- armazenamento local apenas de IDs acompanhados de contas/pagamentos para facilitar a navegação;
- proxy Vite para o API Gateway durante o desenvolvimento.

## Rotas da SPA

```text
/login
/
/accounts
/payments
/ledger
/fraud
/profile
```

## Integração com o backend

O frontend respeita os contratos atuais dos serviços e não inventa endpoints de listagem global inexistentes. Contas e pagamentos são criados/consultados pelos endpoints reais e os IDs acompanhados ficam no navegador para facilitar o uso da interface.

Fluxo principal:

```text
Browser :5173
   |
   v
Vite proxy
   |
   v
API Gateway :8080
   |
   +--> Auth :8085
   +--> Payment :8081
   +--> Account :8082
   +--> Ledger :8083
   +--> Fraud :8084
```

## Segurança

- o JWT de acesso fica em `sessionStorage`;
- senha não é persistida no navegador;
- rotas da SPA usam proteção de autenticação;
- autorização efetiva continua no Gateway e nos microsserviços;
- a tela de fraude respeita a permissão `FRAUD_READ`;
- o modelo JWT permanece HS256 com segredo compartilhado no ambiente atual de estudo/portfólio.

## Validação da Sprint 9

Os controles de build foram aprovados:

```text
Full Maven reactor                         PASS
Frontend dependencies                      PASS
Frontend production build                  PASS
```

Os controles de runtime também foram aprovados:

```text
Frontend dev server + SPA routes            PASS
Frontend proxy reaches Gateway              PASS
Register/login through frontend             PASS
JWT auth/me through frontend                PASS
Account create/read through frontend         PASS
PIX create/read through frontend             PASS
Anonymous protection through frontend        PASS
```

Total: **10/10 controles aprovados**.

Validador completo:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-sprint9-frontend.ps1
```

Validador runtime para Windows/PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-sprint9-runtime.ps1
```

## Execução local

```powershell
cd frontend
npm install
npm run dev
```

Abrir:

```text
http://localhost:5173
```

## Limitações atuais

- não existe endpoint global de listagem de contas ou pagamentos no backend atual;
- não existe object-level authorization/ownership por recurso;
- o frontend acompanha IDs localmente para consultas posteriores;
- não há refresh token, revogação, MFA ou recuperação de senha nesta sprint;
- os microsserviços ainda podem ser acessados diretamente pelas portas locais durante desenvolvimento;
- não há deploy cloud nesta sprint.

## Próxima etapa

Sprint 10 — CI/CD e cloud/deployment.
