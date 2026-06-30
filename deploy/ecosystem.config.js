module.exports = {
  apps: [{
    name: 'iona-salgados',
    script: 'src/index.js',
    cwd: __dirname + '/../server',
    instances: 1,
    autorestart: true,
    watch: false,
    max_memory_restart: '500M',
    env_production: {
      NODE_ENV: 'production',
      PORT: 3000,
      HOST: '127.0.0.1',
      SERVE_WEB: 'false'
    }
  }]
};
