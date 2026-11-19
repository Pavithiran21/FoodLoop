// The ticket-stub colour and a spoken name for each category's picture (the drawings are in FoodPicture.jsx).
const STUBS = {
  BAKERY: { stub: 'var(--butter)', name: 'bakery' },
  COOKED_MEAL: { stub: 'var(--peach)', name: 'cooked meal' },
  FRUIT_VEG: { stub: 'var(--sage-stub)', name: 'fruit and veg' },
  PACKAGED: { stub: 'var(--pink-stub)', name: 'packaged food' },
};

export const stubColour = (category) => (STUBS[category] ?? STUBS.BAKERY).stub;

export const pictureName = (category) => (STUBS[category] ?? STUBS.BAKERY).name;
