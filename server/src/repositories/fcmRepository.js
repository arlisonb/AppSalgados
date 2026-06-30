const { getDb } = require('../database/db');

function upsertToken(token, deviceName) {
  getDb().prepare(`
    INSERT INTO fcm_tokens (token, device_name, updated_at)
    VALUES (?, ?, datetime('now', 'localtime'))
    ON CONFLICT(token) DO UPDATE SET
      device_name = excluded.device_name,
      updated_at = datetime('now', 'localtime')
  `).run(token, deviceName || null);
}

function findAllTokens() {
  return getDb().prepare('SELECT token FROM fcm_tokens').all().map((r) => r.token);
}

function removeToken(token) {
  getDb().prepare('DELETE FROM fcm_tokens WHERE token = ?').run(token);
}

module.exports = { upsertToken, findAllTokens, removeToken };
