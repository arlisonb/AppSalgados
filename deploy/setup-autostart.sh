#!/bin/bash
# Instala reinício automático do Iona Salgados na VPS (systemd).
# Uso: sudo bash deploy/setup-autostart.sh
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/iona-salgados}"
SERVICE_NAME="iona-salgados"
SERVICE_FILE="/etc/systemd/system/${SERVICE_NAME}.service"

if [[ $EUID -ne 0 ]]; then
  echo "Execute como root: sudo bash $0"
  exit 1
fi

if [[ ! -f "$APP_DIR/server/src/index.js" ]]; then
  echo "App não encontrado em $APP_DIR"
  echo "Clone o repositório em $APP_DIR antes de continuar."
  exit 1
fi

if [[ ! -f "$APP_DIR/server/.env" ]]; then
  echo "Arquivo $APP_DIR/server/.env não encontrado."
  echo "Copie .env.production.example para .env e configure."
  exit 1
fi

NODE_BIN="$(command -v node || true)"
if [[ -z "$NODE_BIN" ]]; then
  echo "Node.js não encontrado. Instale com: curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && apt install -y nodejs"
  exit 1
fi

echo "==> Parando PM2 iona-salgados (se existir) para evitar conflito na porta 3000..."
if command -v pm2 >/dev/null 2>&1; then
  pm2 delete iona-salgados 2>/dev/null || true
  pm2 save 2>/dev/null || true
fi

echo "==> Instalando serviço systemd..."
sed "s|/usr/bin/node|$NODE_BIN|g; s|/opt/iona-salgados|$APP_DIR|g" \
  "$APP_DIR/deploy/iona-salgados.service" > "$SERVICE_FILE"

systemctl daemon-reload
systemctl enable "$SERVICE_NAME"
systemctl restart "$SERVICE_NAME"

echo ""
echo "Serviço instalado e ativo."
echo "  status:  systemctl status $SERVICE_NAME"
echo "  logs:    journalctl -u $SERVICE_NAME -f"
echo "  parar:   systemctl stop $SERVICE_NAME"
echo "  iniciar: systemctl start $SERVICE_NAME"
echo ""
systemctl --no-pager status "$SERVICE_NAME" || true
