import cityData from './cities-2023.json';

export const cities = cityData.map(([code, name, province, level]) => ({
  code: code!,
  name: name!,
  province: province!,
  level: level!,
  value: name!,
}));

const groups = new Map<string, Array<{ value: string; label: string }>>();
for (const city of cities) {
  const options = groups.get(city.province) ?? [];
  options.push({ value: city.value, label: `${city.name} · ${city.province}` });
  groups.set(city.province, options);
}
export const cityOptions = [...groups].map(([label, options]) => ({ label, options }));

export function normalizeCityName(value: string): string {
  return cities.find(city => city.value === value || city.name.slice(0, -1) === value)?.value ?? value;
}
