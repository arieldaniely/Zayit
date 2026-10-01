import assert from 'node:assert/strict';
import { test } from 'node:test';
import { distributionName, distributionOrder, isDistributionArchive } from './src/distributions.ts';

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
