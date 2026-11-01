import assert from 'node:assert/strict';
import { test } from 'node:test';
import { build } from 'esbuild';
import { fileURLToPath } from 'node:url';

const bundled = await build({
  entryPoints: [fileURLToPath(new URL('./src/distributions.ts', import.meta.url))],
  bundle: true, platform: 'node', format: 'esm', write: false,
});
const { distributionName, distributionOrder, isDistributionArchive, isOwnDistributionUrl } =
  await import(`data:text/javascript;base64,${Buffer.from(bundled.outputFiles[0].text).toString('base64')}`);

test('all six distributions include every numbered part', () => {
  for (const name of distributionOrder) {
    for (const suffix of ['', '.part01', '.part02', '.part03', '.part10', '.part100']) {
      const asset = `${name}.tar.zst${suffix}`;
      assert.equal(isDistributionArchive(asset), true);
      assert.equal(distributionName(asset), name);
    }
  }
});

test('exclude manifests, unrelated archives and malformed parts', () => {
  for (const asset of ['distributions.json', 'semantic-bundle.json', 'other.tar.zst.part03',
    'seforim_bundle.tar.zst.part', 'seforim_bundle.tar.zst.part03.tmp']) {
    assert.equal(isDistributionArchive(asset), false);
  }
});

test('numeric ordering puts part100 after part10', () => {
  const names = ['part100', 'part03', 'part10', 'part02'];
  assert.deepEqual(names.sort((a, b) => a.localeCompare(b, undefined, { numeric: true })),
    ['part02', 'part03', 'part10', 'part100']);
});

test('download links are restricted to our library fork', () => {
  assert.equal(isOwnDistributionUrl('https://github.com/arieldaniely/SeforimLibrary/releases/download/full/seforim_bundle.tar.zst.part04'), true);
  for (const url of [
    'https://github.com/kdroidFilter/SeforimLibrary/releases/download/full/seforim_bundle.tar.zst',
    'https://example.com/arieldaniely/SeforimLibrary/releases/download/full/file',
    'https://github.com/arieldaniely/SeforimLibrary-other/releases/download/full/file',
    'invalid-url',
  ]) assert.equal(isOwnDistributionUrl(url), false);
});
