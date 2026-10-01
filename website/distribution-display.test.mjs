import assert from 'node:assert/strict';
import { test } from 'node:test';
import { build } from 'esbuild';
import { fileURLToPath } from 'node:url';

const bundled = await build({
  stdin: {
    contents: `import React from 'react';
      import { renderToStaticMarkup } from 'react-dom/server';
      import { DatabaseSection } from './src/pages/DownloadPage.tsx';
      export function render(assets) {
        return renderToStaticMarkup(React.createElement(DatabaseSection, {
          dbLoading: false, dbError: null, dbAssets: assets, t: key => key,
        }));
      }`,
    resolveDir: fileURLToPath(new URL('.', import.meta.url)),
  },
  bundle: true, platform: 'node', format: 'esm', write: false,
  jsx: 'automatic',
  banner: { js: `import { createRequire } from 'node:module'; const require = createRequire(${JSON.stringify(import.meta.url)});` },
  external: ['node:*'],
});
// Bundling avoids requiring a separate test compiler for the TSX component.
const { render } = await import(`data:text/javascript;base64,${Buffer.from(bundled.outputFiles[0].text).toString('base64')}`);
const asset = (name, owner = 'arieldaniely') => ({
  id: name, name, size: '1 MB', rawSize: 1024,
  url: `https://github.com/${owner}/SeforimLibrary/releases/download/full/${name}`,
  sha256: 'not-user-facing',
});

test('full package is prominent, all parts are visible, alternatives and supplements are separated', () => {
  const html = render([
    ...[1, 2, 3, 4].map(i => asset(`seforim_bundle.tar.zst.part0${i}`)),
    asset('seforim_bundle-database-only.tar.zst'), asset('semantic-bundle.tar.zst'),
  ]);
  const recommended = html.match(/<article[^>]*download-distribution-recommended[^>]*>(.*?)<\/article>/s)[1];
  assert.equal((recommended.match(/href=/g) || []).length, 4);
  assert.equal(recommended.includes('<details'), false);
  assert.ok(html.indexOf('download-distribution-recommended') < html.indexOf('download-distribution-alternatives'));
  assert.ok(html.indexOf('download-distribution-alternatives') < html.indexOf('download-distribution-supplements'));
  assert.equal((html.match(/<details/g) || []).length, 2);
  assert.equal(html.includes('SHA'), false);
  assert.equal(html.includes('not-user-facing'), false);
  assert.equal(html.includes('<select'), false);
});

test('no upstream downloads, checksums, manifests or unrelated files', () => {
  const html = render([
    asset('seforim_bundle.tar.zst'), asset('seforim_bundle-no-pdf.tar.zst', 'kdroidFilter'),
    asset('checksums.sha256'), asset('semantic-bundle.json'), asset('other.tar.zst'),
  ]);
  for (const text of ['kdroidFilter', 'checksums.sha256', 'semantic-bundle.json', 'other.tar.zst']) {
    assert.equal(html.includes(text), false);
  }
});

test('a partial package never becomes the recommended full package', () => {
  const html = render([asset('seforim_bundle-database-only.tar.zst')]);
  assert.ok(html.includes('dl.database.fullUnavailable'));
  assert.equal(html.includes('download-distribution-recommended'), false);
});
