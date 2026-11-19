/** Which ids an input should point at with aria-describedby: its error first, then its hint. */
export function describedBy(id, { error, hint }) {
  const ids = [];
  if (error) {
    ids.push(`${id}-error`);
  }
  if (hint) {
    ids.push(`${id}-hint`);
  }
  return ids.length ? ids.join(' ') : undefined;
}
