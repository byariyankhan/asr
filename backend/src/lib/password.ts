/** The one password rule, shared by Better Auth, the app's copy and the web reset form. */
export const MIN_PASSWORD = 8;

/**
 * How many digits a password-reset code has.
 *
 * Three things have to agree on this: Better Auth generates the code from
 * it, the email says it, and the app draws exactly this many boxes. They
 * agree because they read it here -- except the app, which cannot, and so
 * has its own copy under a test that fails if the two drift.
 */
export const RESET_CODE_LENGTH = 7;
