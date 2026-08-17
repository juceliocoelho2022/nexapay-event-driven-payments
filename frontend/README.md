# NexaPay Frontend — Sprint 9

Frontend React + TypeScript do NexaPay.

## Stack

- React 19
- TypeScript
- Vite 8
- React Router
- Fetch API nativa

## Execução local

O desenvolvimento usa proxy do Vite para o API Gateway em `http://localhost:8080`, evitando dependência de CORS no ambiente local.

```powershell
cd frontend
npm install --package-lock=false
npm run dev
```

Abra `http://localhost:5173`.

## Fluxos disponíveis

- cadastro e login;
- dashboard;
- criação, consulta, crédito e débito de contas;
- criação e consulta de pagamentos PIX com `Idempotency-Key`;
- consulta do Ledger por conta;
- consulta de decisão antifraude para usuários com `FRAUD_READ`;
- visualização de roles e permissions.

O access token é mantido em `sessionStorage`. O `localStorage` é usado somente para guardar IDs recentes de contas e pagamentos acompanhados pelo navegador.

## API

Por padrão, chamadas `/api/**` e `/actuator/**` são encaminhadas pelo Vite para `http://localhost:8080`.

Para um ambiente onde frontend e Gateway tenham origens separadas, configure:

```text
VITE_API_BASE_URL=https://gateway.exemplo.com
```

Nesse cenário, o Gateway também precisa de política CORS apropriada ou de um reverse proxy de mesma origem.
