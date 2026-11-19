import { useRef } from 'react';

/**
 * Pill tabs as a real tablist: the selected tab is in the Tab order, the arrow keys (and Home and End) move
 * between tabs. Used for My claims, Verify donors and the Reports queue, where each tab is a filter on one list.
 */
export function Tabs({ label, tabs, value, onChange, className = '' }) {
  const refs = useRef([]);

  function onKeyDown(event, index) {
    let next = null;
    if (event.key === 'ArrowRight') next = (index + 1) % tabs.length;
    if (event.key === 'ArrowLeft') next = (index - 1 + tabs.length) % tabs.length;
    if (event.key === 'Home') next = 0;
    if (event.key === 'End') next = tabs.length - 1;
    if (next !== null) {
      event.preventDefault();
      refs.current[next]?.focus();
      onChange(tabs[next].value);
    }
  }

  return (
    <div className={`tabs ${className}`} role="tablist" aria-label={label}>
      {tabs.map((tab, index) => {
        const selected = tab.value === value;
        return (
          <button
            key={tab.value || 'all'}
            ref={(element) => {
              refs.current[index] = element;
            }}
            type="button"
            role="tab"
            aria-selected={selected}
            tabIndex={selected ? 0 : -1}
            onClick={() => onChange(tab.value)}
            onKeyDown={(event) => onKeyDown(event, index)}
          >
            {tab.label}
          </button>
        );
      })}
    </div>
  );
}
