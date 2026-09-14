/*
 * Bug do WppConnect 2.2.x: com phoneNumber, loginByCode retorna ANTES de
 * gravar this.urlCode. checkQrCode acha que o QR mudou sempre e dispara
 * genLinkDeviceCode em loop (429 / código inválido).
 *
 * Correção: gravar urlCode antes de pedir o código. Assim cada QR gera
 * UM código novo e o app pode mostrar o código ainda válido.
 */
const fs = require('fs');
const path = require('path');

const file = path.join(
  __dirname,
  '..',
  'node_modules',
  '@wppconnect-team',
  'wppconnect',
  'dist',
  'api',
  'layers',
  'host.layer.js'
);

if (!fs.existsSync(file)) {
  console.warn('[patch-wppconnect] WPPConnect não encontrado; patch ignorado.');
  process.exit(0);
}

let source = fs.readFileSync(file, 'utf8');

if (source.includes('pairingUrlCodeFix')) {
  console.log('[patch-wppconnect] Patch de urlCode já aplicado.');
  process.exit(0);
}

// Remove o patch antigo (linkCodeIssued), se existir.
source = source
  .replace(/\s*this\.linkCodeIssued = false;\n/, '\n')
  .replace(
    /if \(typeof this\.options\.phoneNumber === 'string'\) \{\s*if \(this\.linkCodeIssued\) \{\s*return;\s*\}\s*this\.linkCodeIssued = true;\s*return this\.loginByCode\(this\.options\.phoneNumber\);\s*\}/,
    "if (typeof this.options.phoneNumber === 'string') {\n            return this.loginByCode(this.options.phoneNumber);\n        }"
  );

const needle =
  "if (!result?.urlCode || this.urlCode === result.urlCode) {\n            return;\n        }\n        if (typeof this.options.phoneNumber === 'string') {\n            return this.loginByCode(this.options.phoneNumber);\n        }\n        this.urlCode = result.urlCode;\n        this.attempt++;";

const replacement =
  "if (!result?.urlCode || this.urlCode === result.urlCode) {\n            return;\n        }\n        // pairingUrlCodeFix: grava o QR atual ANTES do loginByCode\n        this.urlCode = result.urlCode;\n        this.attempt++;\n        if (typeof this.options.phoneNumber === 'string') {\n            return this.loginByCode(this.options.phoneNumber);\n        }";

if (!source.includes(needle)) {
  console.warn('[patch-wppconnect] Estrutura inesperada; patch não aplicado.');
  process.exit(0);
}

source = source.replace(needle, replacement);
fs.writeFileSync(file, source);
console.log('[patch-wppconnect] urlCode gravado antes do código digitável.');
