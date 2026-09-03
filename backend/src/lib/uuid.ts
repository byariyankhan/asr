import { uuidv7 } from "uuidv7";

// Every primary key in this database is a uuid column. A value that is not
// even uuid-shaped must be refused BEFORE it reaches a query: Postgres answers
// `invalid input syntax for type uuid` with an error, not an empty result, and
// a route comparing a client-supplied string against an id column would turn
// that into a 500. Devices generate their own event ids (UUIDv7), so this is
// checked on every id that arrives from a phone.
export const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function isUuidLike(value: unknown): value is string {
  return typeof value === "string" && UUID_RE.test(value);
}

export function newId(): string {
  return uuidv7();
}
