import { useId } from 'react';

/** Ids for a dialog's title and text: const { titleId, textId } = useDialogIds(). */
export function useDialogIds() {
  const base = useId();
  return { titleId: `${base}-title`, textId: `${base}-text` };
}
