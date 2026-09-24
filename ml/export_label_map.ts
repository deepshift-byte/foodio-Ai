import { writeFileSync } from 'fs';
import { FOODS } from '../shared/foods';
// Extra English names that public datasets commonly use for these foods.
const EXTRA: Record<string, string[]> = {
  posho: ['ugali', 'nshima', 'sadza', 'pap', 'banku'],
  matooke: ['matoke', 'steamed plantain', 'mashed plantain', 'green banana'],
  'cassava-boiled': ['boiled cassava', 'cassava'],
  rice: ['white rice', 'plain rice'],
  chapati: ['chapatti', 'flatbread'],
  'beans': ['bean stew', 'red beans', 'githeri'],
  'gnut-sauce': ['groundnut soup', 'peanut soup', 'peanut stew', 'maafe', 'mafe'],
  'jollof-rice': ['jollof', 'jollof rice'],
  fufu: ['fufu', 'pounded yam', 'eba', 'garri'],
  'egusi-soup': ['egusi', 'egusi soup'],
  'sukuma-wiki': ['collard greens', 'kale'],
  mandazi: ['mandazi', 'puff puff', 'mahamri', 'bofrot'],
  samosa: ['samosa', 'sambusa'],
  'muchomo': ['nyama choma', 'suya', 'grilled meat', 'kebab'],
  'tilapia-fried': ['fried fish'],
  'tilapia-stew': ['fish stew', 'tilapia'],
  'chicken-stew': ['chicken stew', 'chicken curry'],
  'beef-stew': ['beef stew'],
  injera: ['injera', 'enjera'],
  'egg-boiled': ['boiled egg', 'eggs'],
  'sweet-potato': ['sweet potato', 'sweet potatoes'],
  'kachumbari': ['tomato salad', 'kachumbari'],
};
const map: Record<string, string[]> = {};
for (const f of FOODS) {
  const names = [f.id, f.name, ...f.localNames, ...(EXTRA[f.id] ?? [])].map((n) => n.toLowerCase());
  map[f.id] = [...new Set(names)];
}
writeFileSync(new URL('./label_map.json', import.meta.url), JSON.stringify(map, null, 2) + '\n');
