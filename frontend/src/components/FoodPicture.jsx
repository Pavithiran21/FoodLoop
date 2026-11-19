// Flat food pictures, one per category, shown whenever a listing has no photo (and in empty states).
// All drawn on a 120 x 100 box.

function Bread() {
  return (
    <>
      <ellipse cx="60" cy="84" rx="46" ry="6" fill="#000" opacity=".08" />
      <path d="M18 60c0-22 18-34 42-34s42 12 42 34v16a6 6 0 0 1-6 6H24a6 6 0 0 1-6-6z" fill="#e2a25a" />
      <path d="M24 58c0-18 15-28 36-28s36 10 36 28" fill="#f0bd7a" />
      <g stroke="#c47f35" strokeWidth="4" strokeLinecap="round">
        <path d="M42 40l8 10M58 36l8 10M74 40l8 10" />
      </g>
    </>
  );
}

function VegCrate() {
  return (
    <>
      <ellipse cx="60" cy="88" rx="48" ry="6" fill="#000" opacity=".08" />
      <circle cx="40" cy="44" r="16" fill="#e5533d" />
      <circle cx="66" cy="38" r="14" fill="#ffb347" />
      <path d="M78 48l18-26 6 4-16 28z" fill="#f2862e" />
      <path d="M96 22l8-6M98 26l8-2" stroke="#3d8b4f" strokeWidth="4" strokeLinecap="round" />
      <circle cx="56" cy="54" r="12" fill="#6bbf59" />
      <rect x="16" y="54" width="88" height="32" rx="6" fill="#b07a4a" />
      <path d="M16 66h88M16 76h88" stroke="#8f5f35" strokeWidth="3" />
    </>
  );
}

function MealBox() {
  return (
    <>
      <ellipse cx="60" cy="86" rx="46" ry="6" fill="#000" opacity=".08" />
      <rect x="16" y="34" width="88" height="48" rx="10" fill="#fff" />
      <rect x="16" y="34" width="88" height="48" rx="10" fill="none" stroke="#d9d2c3" strokeWidth="3" />
      <rect x="24" y="42" width="38" height="32" rx="6" fill="#f5d36b" />
      <rect x="66" y="42" width="30" height="14" rx="5" fill="#6bbf59" />
      <rect x="66" y="60" width="30" height="14" rx="5" fill="#d9603b" />
      <path d="M30 20q6-8 0-14M44 22q6-8 0-14M58 20q6-8 0-14" stroke="#c9c2b2" strokeWidth="3" fill="none" strokeLinecap="round" />
    </>
  );
}

function Fruit() {
  return (
    <>
      <ellipse cx="60" cy="86" rx="46" ry="6" fill="#000" opacity=".08" />
      <path d="M20 70c8-30 40-40 60-30-10 6-8 22-28 34-14 8-28 6-32-4z" fill="#ffd34d" />
      <circle cx="74" cy="60" r="18" fill="#d93f3f" />
      <path d="M74 42c2-6 6-8 10-8" stroke="#3d8b4f" strokeWidth="4" fill="none" strokeLinecap="round" />
      <circle cx="94" cy="72" r="12" fill="#ff9a3c" />
    </>
  );
}

function Packaged() {
  return (
    <>
      <ellipse cx="60" cy="86" rx="46" ry="6" fill="#000" opacity=".08" />
      <rect x="22" y="30" width="34" height="52" rx="5" fill="#5b8def" />
      <rect x="26" y="40" width="26" height="14" rx="3" fill="#fff" />
      <rect x="62" y="44" width="38" height="38" rx="6" fill="#f2862e" />
      <rect x="68" y="52" width="26" height="10" rx="3" fill="#ffe6a3" />
      <rect x="40" y="22" width="12" height="10" rx="2" fill="#3a6fd0" />
    </>
  );
}

function Drawing({ category }) {
  switch (category) {
    case 'COOKED_MEAL':
      return <MealBox />;
    case 'FRUIT_VEG':
      return <VegCrate />;
    case 'PACKAGED':
      return <Packaged />;
    case 'FRUIT':
      return <Fruit />;
    default:
      return <Bread />;
  }
}

/** A category's picture (decorative unless `label` is given). `category` may also be 'FRUIT' for empty states. */
export function FoodPicture({ category, width = 90, height, label, style }) {
  return (
    <svg
      width={width}
      height={height ?? Math.round((width * 100) / 120)}
      viewBox="0 0 120 100"
      role={label ? 'img' : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : 'true'}
      style={style}
    >
      <Drawing category={category} />
    </svg>
  );
}
