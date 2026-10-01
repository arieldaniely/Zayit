export const distributionOrder = [
  'seforim_bundle',
  'seforim_bundle-no-pdf',
  'seforim_bundle-no-vectors',
  'seforim_bundle-database-only',
  'talmud_bavli_latest',
  'semantic-bundle',
];

export function distributionName(name: string): string {
  if (name === 'semantic-bundle.json') return 'semantic-bundle';
  return name.replace(/\.tar\.zst(?:\.part\d+)?$/, '');
}

export function isDistributionArchive(name: string): boolean {
  return /\.tar\.zst(?:\.part\d+)?$/.test(name) && distributionOrder.includes(distributionName(name));
}

export const libraryReleasesUrl = 'https://github.com/arieldaniely/SeforimLibrary/releases';

export function isOwnDistributionUrl(url: string): boolean {
  try {
    const parsed = new URL(url);
    return parsed.origin === 'https://github.com' && parsed.pathname.startsWith('/arieldaniely/SeforimLibrary/releases/download/');
  } catch {
    return false;
  }
}
