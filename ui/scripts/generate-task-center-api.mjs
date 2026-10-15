import { readFile, writeFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';

export function generateTaskCenterTypes(document) {
  const schemas = document.components?.schemas;
  const pending = ['TaskCenterOverviewResponse', 'CursorPageAsyncTaskResponse'];
  const definitions = new Map();
  function typeOf(schema) {
    if (schema.$ref) {
      const name = schema.$ref.split('/').at(-1);
      if (!definitions.has(name) && !pending.includes(name)) pending.push(name);
      return name;
    }
    if (Array.isArray(schema.type)) return schema.type.map(type => typeOf({ ...schema, type })).join(' | ');
    if (schema.type === 'null') return 'null';
    if (schema.type === 'array') return `Array<${typeOf(schema.items)}>`;
    if (schema.enum) return schema.enum.map(value => JSON.stringify(value)).join(' | ');
    if (schema.type === 'string' || schema.type === 'boolean') return schema.type;
    if (schema.type === 'integer' || schema.type === 'number') return 'number';
    throw new Error('任务中心 OpenAPI 出现未支持的字段结构，请更新生成器');
  }
  while (pending.length) {
    const name = pending.shift();
    const schema = schemas?.[name];
    if (!schema?.properties) throw new Error(`缺少任务中心 Schema: ${name}`);
    definitions.set(name, '');
    const properties = Object.entries(schema.properties).map(([key, value]) => {
      const required = schema.required?.includes(key);
      return `  ${JSON.stringify(key)}${required ? '' : '?'}: ${typeOf(value)}${required ? '' : ' | null'};`;
    });
    definitions.set(name, `export interface ${name} {\n${properties.join('\n')}\n}`);
  }
  return [...definitions.entries()].sort(([left], [right]) => left.localeCompare(right))
    .map(([, definition]) => definition).join('\n\n') + '\n';
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  if (!process.argv[2]) throw new Error('用法: node scripts/generate-task-center-api.mjs <OpenAPI JSON>');
  const source = JSON.parse(await readFile(process.argv[2], 'utf8'));
  await writeFile(new URL('../src/api/taskCenter.generated.ts', import.meta.url), generateTaskCenterTypes(source));
}
