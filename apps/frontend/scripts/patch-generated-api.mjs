import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const target = join(process.cwd(), 'src/api/generated/core/serverSentEvents.gen.ts');
const source = readFileSync(target, 'utf8');

const before = `        const abortHandler = () => {\n          try {\n            reader.cancel();\n          } catch {\n            // noop\n          }\n        };`;
const after = `        const abortHandler = () => {\n          void reader.cancel().catch(() => {\n            // noop\n          });\n        };`;

if (source.includes(after)) {
  process.exit(0);
}

if (!source.includes(before)) {
  throw new Error('Expected abortHandler block not found in generated SSE client');
}

writeFileSync(target, source.replace(before, after), 'utf8');
