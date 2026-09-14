/*
 * WppConnect 2.2.x + código digitável:
 * 1) loginByCode rodava em loop no mesmo QR (429) porque urlCode só era
 *    gravado DEPOIS. Corrigimos gravando urlCode antes.
 * 2) Cada rotação de QR (~20s) pedia um código NOVO e invalidava o que
 *    o cliente estava digitando. Depois do primeiro código, não gera mais.
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
if (source.includes('pairingCodeOnceFix')) {
  console.log('[patch-wppconnect] Patch de código único já aplicado.');
  process.exit(0);
}

const loginBlock = `if (typeof this.options.phoneNumber === 'string') {
            if (this.linkCodeIssued) {
                return;
            }
            this.linkCodeIssued = true;
            return this.loginByCode(this.options.phoneNumber);
        }`;

const onceBlock = `if (!result?.urlCode || this.urlCode === result.urlCode) {
            return;
        }
        // pairingCodeOnceFix: um código por sessão unpaired
        this.urlCode = result.urlCode;
        this.attempt++;
        ${loginBlock}`;

const variants = [
  `if (!result?.urlCode || this.urlCode === result.urlCode) {
            return;
        }
        // pairingUrlCodeFix: grava o QR atual ANTES do loginByCode
        this.urlCode = result.urlCode;
        this.attempt++;
        if (typeof this.options.phoneNumber === 'string') {
            return this.loginByCode(this.options.phoneNumber);
        }`,
  `if (!result?.urlCode || this.urlCode === result.urlCode) {
            return;
        }
        if (typeof this.options.phoneNumber === 'string') {
            return this.loginByCode(this.options.phoneNumber);
        }
        this.urlCode = result.urlCode;
        this.attempt++;`
];

let applied = false;
for (const variant of variants) {
  if (source.includes(variant)) {
    source = source.replace(variant, onceBlock);
    applied = true;
    break;
  }
}

if (!applied) {
  console.warn('[patch-wppconnect] Estrutura inesperada; patch não aplicado.');
  process.exit(0);
}

const resetNeedle = 'if (!needScan) {\n            this.attempt = 0;\n            return;\n        }';
const resetReplacement = 'if (!needScan) {\n            this.attempt = 0;\n            this.linkCodeIssued = false;\n            return;\n        }';
if (source.includes(resetNeedle) && !source.includes('this.linkCodeIssued = false')) {
  source = source.replace(resetNeedle, resetReplacement);
}

fs.writeFileSync(file, source);
console.log('[patch-wppconnect] Código digitável único aplicado.');
