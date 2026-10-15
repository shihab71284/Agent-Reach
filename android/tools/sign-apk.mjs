import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

const [input, output, keyPath, certificatePath, modulesRoot] = process.argv.slice(2);
if (!input || !output || !keyPath || !certificatePath || !modulesRoot) {
  throw new Error('Usage: sign-apk.mjs <input> <output> <key.pem> <certificate.pem> <npm-project>');
}
const library = await import(pathToFileURL(join(modulesRoot, 'node_modules/apk_sign_ts/dist/index.js')).href);
const signer = new library.ApkSigner({
  signingKey: library.SigningKey.fromPEM(
    readFileSync(keyPath, 'utf8'),
    readFileSync(certificatePath, 'utf8'),
  ),
  digestAlgorithm: 'SHA-256',
});
const apk = new Uint8Array(readFileSync(input));
const { signedApk, signatureSize } = await signer.sign(apk);
writeFileSync(output, signedApk);
console.log(`Signed ${output} (${signedApk.length} bytes; ${signatureSize}-byte signing block)`);
