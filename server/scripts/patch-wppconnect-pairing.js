/*
 * WPPConnect 2.2.x regenerates the link-by-phone code whenever WhatsApp Web
 * rotates its QR. That invalidates the code while the customer is typing it.
 * Keep one code per unpaired browser session until upstream releases the fix.
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
if (source.includes('this.linkCodeIssued')) {
  console.log('[patch-wppconnect] Patch de código digitável já aplicado.');
  process.exit(0);
}

const resetNeedle = 'if (!needScan) {\n            this.attempt = 0;\n            return;\n        }';
const resetReplacement = 'if (!needScan) {\n            this.attempt = 0;\n            this.linkCodeIssued = false;\n            return;\n        }';
const linkNeedle = "if (typeof this.options.phoneNumber === 'string') {\n            return this.loginByCode(this.options.phoneNumber);\n        }";
const linkReplacement = "if (typeof this.options.phoneNumber === 'string') {\n            if (this.linkCodeIssued) {\n                return;\n            }\n            this.linkCodeIssued = true;\n            return this.loginByCode(this.options.phoneNumber);\n        }";

if (!source.includes(resetNeedle) || !source.includes(linkNeedle)) {
  console.warn('[patch-wppconnect] Estrutura inesperada; patch não aplicado.');
  process.exit(0);
}

source = source.replace(resetNeedle, resetReplacement).replace(linkNeedle, linkReplacement);
fs.writeFileSync(file, source);
console.log('[patch-wppconnect] Código digitável estabilizado.');
