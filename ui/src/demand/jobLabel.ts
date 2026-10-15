/** Stable business code distinguishes separate demands sharing the same title. */
export const jobLabel = (title: string, code?: string | null) => code ? `${title} · ${code}` : title;
