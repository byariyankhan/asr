/**
 * A code that is malformed, unknown, already answered, or belongs to a
 * deleted account. All four look the same from outside, and saying which
 * would let anybody with the URL probe for real codes.
 */
export default function InviteNotFound() {
  return (
    <main style={page}>
      <div style={card}>
        <p style={eyebrow}>ASR</p>
        <h1 style={title}>This invitation is no longer open.</h1>
        <p style={body}>
          It may have already been accepted, or the person who sent it may have cancelled it. Ask
          them to send a new one — it takes them a few seconds.
        </p>
      </div>
    </main>
  );
}

const page: React.CSSProperties = {
  minHeight: "100vh",
  margin: 0,
  background: "#0A0A0A",
  color: "#F5F5F2",
  fontFamily: "system-ui, -apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif",
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  padding: 24,
  boxSizing: "border-box",
};
const card: React.CSSProperties = {
  width: "100%",
  maxWidth: 420,
  background: "#0E1110",
  border: "1px solid #212926",
  borderRadius: 24,
  padding: "32px 26px",
  textAlign: "center",
};
const eyebrow: React.CSSProperties = {
  margin: "0 0 18px",
  color: "#12B886",
  fontSize: 11,
  letterSpacing: "0.14em",
  fontWeight: 600,
};
const title: React.CSSProperties = { margin: "0 0 12px", fontSize: 24, fontWeight: 700 };
const body: React.CSSProperties = { margin: 0, color: "#9A9F9C", fontSize: 14, lineHeight: 1.6 };
