// Gera o payload "PIX Copia e Cola" (BR Code / padrão EMV do Banco Central)
// a partir de uma chave PIX e um valor. O código resultante pode ser colado
// no app do banco na opção "PIX Copia e Cola".

function sanitize(text, max) {
  return String(text || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '') // remove acentos
    .replace(/[^A-Za-z0-9 ]/g, '')
    .trim()
    .toUpperCase()
    .substring(0, max);
}

// Campo no formato EMV: ID (2) + tamanho (2) + valor.
function emv(id, value) {
  const len = String(value.length).padStart(2, '0');
  return `${id}${len}${value}`;
}

// CRC16-CCITT (polinômio 0x1021, inicial 0xFFFF).
function crc16(payload) {
  let crc = 0xFFFF;
  for (let i = 0; i < payload.length; i++) {
    crc ^= payload.charCodeAt(i) << 8;
    for (let j = 0; j < 8; j++) {
      crc = (crc & 0x8000) ? ((crc << 1) ^ 0x1021) : (crc << 1);
      crc &= 0xFFFF;
    }
  }
  return crc.toString(16).toUpperCase().padStart(4, '0');
}

function gerarPixCopiaECola({ chave, nome, cidade, valor, txid }) {
  const chaveTrim = String(chave || '').trim();
  if (!chaveTrim) return null;

  const nomeSan = sanitize(nome, 25) || 'RECEBEDOR';
  const cidadeSan = sanitize(cidade, 15) || 'BRASIL';
  const txidSan = String(txid || '***').replace(/[^A-Za-z0-9]/g, '').substring(0, 25) || '***';

  const merchantAccount = emv('26', emv('00', 'br.gov.bcb.pix') + emv('01', chaveTrim));
  const additionalData = emv('62', emv('05', txidSan));

  let payload =
    emv('00', '01') +
    merchantAccount +
    emv('52', '0000') +
    emv('53', '986');

  if (valor != null && Number(valor) > 0) {
    payload += emv('54', Number(valor).toFixed(2));
  }

  payload +=
    emv('58', 'BR') +
    emv('59', nomeSan) +
    emv('60', cidadeSan) +
    additionalData +
    '6304';

  return payload + crc16(payload);
}

module.exports = { gerarPixCopiaECola };
