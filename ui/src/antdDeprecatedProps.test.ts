import ts from 'typescript';
import { describe, expect, it } from 'vitest';

const tsxSources = import.meta.glob('./**/*.tsx', {
  eager: true,
  query: '?raw',
  import: 'default',
});

describe('Ant Design 6 compatibility', () => {
  it('does not use runtime-deprecated component props', () => {
    const violations = Object.entries(tsxSources).flatMap(([path, source]) => {
      const sourceFile = ts.createSourceFile(path, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
      const found: string[] = [];
      const deprecatedProps = new Map([
        ['Spin', 'tip'],
        ['Space', 'direction'],
        ['Statistic', 'valueStyle'],
        ['Alert', 'message'],
        ['Drawer', 'width'],
      ]);

      const visit = (node: ts.Node): void => {
        if (ts.isJsxOpeningElement(node) || ts.isJsxSelfClosingElement(node)) {
          const component = node.tagName.getText(sourceFile);
          const deprecatedProp = deprecatedProps.get(component);
          const attributes = node.attributes.properties.filter(ts.isJsxAttribute);
          if (deprecatedProp && attributes.some((attribute) => attribute.name.getText(sourceFile) === deprecatedProp)) {
            found.push(`${path}: ${component}.${deprecatedProp}`);
          }
          if (component === 'Timeline') {
            const items = attributes.find((attribute) => attribute.name.getText(sourceFile) === 'items');
            if (items?.initializer && containsChildrenProperty(items.initializer)) {
              found.push(`${path}: Timeline.items.children`);
            }
          }
        }
        ts.forEachChild(node, visit);
      };

      const containsChildrenProperty = (node: ts.Node): boolean => {
        if (ts.isPropertyAssignment(node) && node.name.getText(sourceFile) === 'children') return true;
        return node.getChildren(sourceFile).some(containsChildrenProperty);
      };

      visit(sourceFile);
      return found;
    });

    expect(violations).toEqual([]);
  });
});
