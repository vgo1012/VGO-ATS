import { test } from 'node:test';
import assert from 'node:assert/strict';
import { generateTaskCenterTypes } from './generate-task-center-api.mjs';

test('generates referenced schemas deterministically and distinguishes required fields', () => {
  const document = { components: { schemas: {
    TaskCenterOverviewResponse: { properties: { tasks: { type: 'array', items: { $ref: '#/components/schemas/Task' } } } },
    CursorPageAsyncTaskResponse: { properties: { hasMore: { type: 'boolean' } }, required: ['hasMore'] },
    Task: { properties: { id: { type: 'string' }, count: { type: 'integer' } }, required: ['id'] },
  } } };
  const generated = generateTaskCenterTypes(document);
  assert.ok(generated.includes('"id": string;'));
  assert.ok(generated.includes('"count"?: number | null;'));
  assert.ok(generated.includes('Array<Task>'));
  assert.equal(generated, generateTaskCenterTypes(document));
});

test('fails instead of generating misleading types for missing or unsupported schemas', () => {
  assert.throws(() => generateTaskCenterTypes({}));
  assert.throws(() => generateTaskCenterTypes({ components: { schemas: {
    TaskCenterOverviewResponse: { properties: { payload: { type: 'object' } } },
  } } }));
});
