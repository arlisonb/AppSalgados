# Iona Salgados — AppSalgados

Sistema de pedidos para lanchonete de salgados: bot WhatsApp, app Android e servidor Node.js.

## Estrutura

- `server/` — API REST, Socket.IO e bot WhatsApp
- `android/` — app Android (Kotlin + Compose)
- `deploy/` — nginx e PM2 para produção

## Servidor (desenvolvimento)

```bash
cd server
cp .env.example .env
npm install
npm start
```

## Android

Abra a pasta `android/` no Android Studio ou compile:

```bash
cd android
./gradlew assembleRelease
```

## Produção

Veja `server/.env.production.example` e `deploy/nginx-iona.conf`.
