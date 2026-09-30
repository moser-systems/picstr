import { mkdirSync, cpSync } from 'fs';

const dirs = [
  'src/main/resources/static/vendor/tabler/css',
  'src/main/resources/static/vendor/tabler/js',
  'src/main/resources/static/vendor/tom-select/js',
  'src/main/resources/static/vendor/tom-select/css',
  'src/main/resources/static/vendor/leaflet/images',
  'src/main/resources/static/vendor/hyperscript',
  'src/main/resources/static/vendor/htmx',
  'src/main/resources/static/vendor/inter/files',
];

for (const dir of dirs) {
  mkdirSync(dir, { recursive: true });
}

const copies = [
  ['node_modules/@tabler/core/dist/css/tabler.min.css', 'src/main/resources/static/vendor/tabler/css/tabler.min.css'],
  ['node_modules/@tabler/core/dist/css/tabler-themes.min.css', 'src/main/resources/static/vendor/tabler/css/tabler-themes.min.css'],
  ['node_modules/@tabler/core/dist/css/tabler-vendors.min.css', 'src/main/resources/static/vendor/tabler/css/tabler-vendors.min.css'],
  ['node_modules/@tabler/core/dist/js/tabler.min.js', 'src/main/resources/static/vendor/tabler/js/tabler.min.js'],
  ['node_modules/@tabler/core/dist/js/tabler-theme.min.js', 'src/main/resources/static/vendor/tabler/js/tabler-theme.min.js'],
  ['node_modules/tom-select/dist/js/tom-select.complete.min.js', 'src/main/resources/static/vendor/tom-select/js/tom-select.complete.min.js'],
  ['node_modules/tom-select/dist/css/tom-select.bootstrap5.min.css', 'src/main/resources/static/vendor/tom-select/css/tom-select.bootstrap5.min.css'],
  ['node_modules/leaflet/dist/leaflet.css', 'src/main/resources/static/vendor/leaflet/leaflet.css'],
  ['node_modules/leaflet/dist/leaflet.js', 'src/main/resources/static/vendor/leaflet/leaflet.js'],
  ['node_modules/hyperscript.org/dist/_hyperscript.min.js', 'src/main/resources/static/vendor/hyperscript/hyperscript.min.js'],
  ['node_modules/htmx.org/dist/htmx.min.js', 'src/main/resources/static/vendor/htmx/htmx.min.js'],
  ['node_modules/leaflet/dist/images/', 'src/main/resources/static/vendor/leaflet/images/'],
  ['node_modules/@fontsource-variable/inter/index.css', 'src/main/resources/static/vendor/inter/inter.css'],
];

for (const [src, dst] of copies) {
  cpSync(src, dst, {
    recursive: true,
  });
  console.log(`Copied ${src} → ${dst}`);
}

// Only the upright variable-weight Inter files referenced by index.css
cpSync('node_modules/@fontsource-variable/inter/files/', 'src/main/resources/static/vendor/inter/files/', {
  recursive: true,
  filter: (src) => !src.endsWith('.woff2') || src.endsWith('-wght-normal.woff2'),
});
console.log('Copied Inter font files');
