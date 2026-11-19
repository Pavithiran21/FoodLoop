import { IconChevronLeft, IconChevronRight } from '@tabler/icons-react';

/** "1–20 of 38" with previous and next buttons, for any paginated list (page is 1-based). */
export function Pager({ data, onPage, note, className = 'pager', showSize = false }) {
  if (!data || data.totalItems === 0) {
    return null;
  }
  const { page, size, totalItems, totalPages } = data;
  const first = (page - 1) * size + 1;
  const last = Math.min(page * size, totalItems);
  return (
    <nav className={className} aria-label="Pages">
      <span className="fill">
        {first}–{last} of {totalItems}
        {note ? ` · ${note}` : ''}
      </span>
      {showSize && <span>{size} per page</span>}
      {totalPages > 1 && (
        <>
          <button type="button" className="icon-btn" aria-label="Previous page" disabled={page <= 1} onClick={() => onPage(page - 1)}>
            <IconChevronLeft size={18} aria-hidden="true" />
          </button>
          <button
            type="button"
            className="icon-btn"
            aria-label="Next page"
            disabled={page >= totalPages}
            onClick={() => onPage(page + 1)}
          >
            <IconChevronRight size={18} aria-hidden="true" />
          </button>
        </>
      )}
    </nav>
  );
}
