import { cert, getApps, initializeApp, type App } from "firebase-admin/app";
import { getMessaging } from "firebase-admin/messaging";

export type PushMessage = { title: string; body: string; data?: Record<string, string> };
export type PushResult = { ok: true; id: string } | { ok: false; unregistered: boolean; error: string };
export type PushSender = (token: string, message: PushMessage) => Promise<PushResult>;

let app: App | null | undefined;

function firebaseApp(): App | null {
  if (app !== undefined) return app;
  const projectId = process.env.FIREBASE_PROJECT_ID;
  const clientEmail = process.env.FIREBASE_CLIENT_EMAIL;
  const privateKey = process.env.FIREBASE_PRIVATE_KEY?.replace(/\\n/g, "\n");
  if (!projectId || !clientEmail || !privateKey) {
    app = null;
    return null;
  }
  app = getApps()[0] ?? initializeApp({ credential: cert({ projectId, clientEmail, privateKey }) });
  return app;
}

export function fcmConfigured(): boolean {
  return firebaseApp() !== null;
}

// Codes FCM returns when a token will never work again: the app was
// uninstalled, or the token was rotated away. Both mean "stop using it";
// the first is also our uninstall signal.
const DEAD_TOKEN_CODES = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
  "messaging/invalid-argument",
]);

export const sendPush: PushSender = async (token, message) => {
  const firebase = firebaseApp();
  if (!firebase) return { ok: false, unregistered: false, error: "fcm_not_configured" };
  try {
    const id = await getMessaging(firebase).send({
      token,
      notification: { title: message.title, body: message.body },
      data: message.data,
      android: { priority: "high" },
    });
    return { ok: true, id };
  } catch (error) {
    const code = (error as { code?: string }).code ?? "unknown";
    return { ok: false, unregistered: DEAD_TOKEN_CODES.has(code), error: code };
  }
};
