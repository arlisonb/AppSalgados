# Iona Salgados — PM2
# Porta 3000 (localhost). Não usar 3001/3002/3003 (outros sistemas na VPS).
# Preferir systemd (deploy/setup-autostart.sh) para reinício após reboot da VPS.
module.exports = {
  apps: [{
    name: 'iona-salgados',
    script: 'src/index.js',
    cwd: __dirname + '/../server',
    instances: 1,
    exec_mode: 'fork',
    autorestart: true,
    watch: false,
    max_memory_restart: '500M',
    min_uptime: '10s',
    max_restarts: 100,
    restart_delay: 5000,
    exp_backoff_restart_delay: 1000,
    kill_timeout: 30000,
    listen_timeout: 10000,
    env_production: {
      NODE_ENV: 'production',
      PORT: 3000,
      HOST: '127.0.0.1',
      SERVE_WEB: 'false',
      PUBLIC_URL: 'https://iona.meuappagenda.com.br'
    }
  }]
};
