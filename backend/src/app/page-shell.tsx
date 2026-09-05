import type { ReactNode } from "react";

/**
 * The one page layout the product's links land on when there is no app to
 * open them: a title, a sentence, and whatever the page needs below. The
 * witness invitation has its own richer page; this is for the plain ones.
 *
 * Inline styles, same as the invitation page and for the same reason: two
 * pages do not justify a stylesheet, and these must render for a mail
 * client's in-app browser with nothing cached.
 */
export function Page({ title, lead, children }: { title: string; lead: string; children?: ReactNode }) {
  return (
    <main
      style={{
        minHeight: "100vh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: 24,
        background: "#0A0A0A",
        color: "#F5F5F5",
        fontFamily: "Inter, system-ui, -apple-system, Segoe UI, Roboto, sans-serif",
      }}
    >
      <div style={{ maxWidth: 440, width: "100%" }}>
        <div style={{ fontSize: 13, letterSpacing: 2, color: "#8A8A8A", marginBottom: 16 }}>ASR</div>
        <h1 style={{ fontSize: 26, lineHeight: 1.25, margin: "0 0 12px", fontWeight: 600 }}>{title}</h1>
        <p style={{ fontSize: 16, lineHeight: 1.5, margin: "0 0 24px", color: "#C9C9C9" }}>{lead}</p>
        {children}
      </div>
    </main>
  );
}

export const field: React.CSSProperties = {
  width: "100%",
  boxSizing: "border-box",
  padding: "14px 16px",
  borderRadius: 12,
  border: "1px solid #2A2A2A",
  background: "#141414",
  color: "#F5F5F5",
  fontSize: 16,
  marginBottom: 12,
};

export const button: React.CSSProperties = {
  width: "100%",
  padding: "14px 16px",
  borderRadius: 12,
  border: "none",
  background: "#F5F5F5",
  color: "#0A0A0A",
  fontSize: 16,
  fontWeight: 600,
  cursor: "pointer",
};
