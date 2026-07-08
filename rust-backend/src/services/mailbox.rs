use crate::{
    config::Config,
    error::AppError,
    models::admin::{InboxMessage, InboxSummary},
};
use lettre::message::header::{Header, HeaderName, HeaderValue};
use std::io::{Read, Write};

/// A message body has to be readable and writable regardless of whether it
/// came in over plain TCP or TLS — `imap::Session` is generic over the stream
/// type, so this lets both paths produce the same concrete `Session<Box<dyn
/// MailStream>>` instead of duplicating every IMAP call per transport.
trait MailStream: Read + Write + Send {}
impl<T: Read + Write + Send> MailStream for T {}

fn open_session(cfg: &Config) -> anyhow::Result<imap::Session<Box<dyn MailStream>>> {
    let addr = (cfg.imap_host.as_str(), cfg.imap_port);
    let stream: Box<dyn MailStream> = if cfg.imap_use_ssl {
        let tls = native_tls::TlsConnector::builder().build()?;
        let tcp = std::net::TcpStream::connect(addr)?;
        Box::new(tls.connect(&cfg.imap_host, tcp)?)
    } else {
        Box::new(std::net::TcpStream::connect(addr)?)
    };
    let client = imap::Client::new(stream);
    client
        .login(&cfg.admin_imap_user, &cfg.admin_imap_pass)
        .map_err(|(e, _)| anyhow::anyhow!("IMAP login failed: {e}"))
}

fn header_value(parsed: &mailparse::ParsedMail, name: &str, fallback: &str) -> String {
    parsed
        .headers
        .iter()
        .find(|h| h.get_key_ref().eq_ignore_ascii_case(name))
        .map(|h| h.get_value())
        .filter(|v| !v.is_empty())
        .unwrap_or_else(|| fallback.to_string())
}

fn escape_html(text: &str) -> String {
    text.replace('&', "&amp;").replace('<', "&lt;").replace('>', "&gt;")
}

/// Mirrors Java's `MailboxService.extractBody`: prefer text/html, fall back to
/// text/plain wrapped in a `<pre>` so it renders sanely in the admin's HTML
/// preview iframe, recurse into nested multipart parts, otherwise a
/// "nothing readable" placeholder.
fn extract_body(part: &mailparse::ParsedMail) -> String {
    let mimetype = part.ctype.mimetype.to_ascii_lowercase();
    if mimetype == "text/html" {
        return part.get_body().unwrap_or_default();
    }
    if mimetype == "text/plain" {
        let text = part.get_body().unwrap_or_default();
        return format!(
            "<pre style='font-family:inherit;white-space:pre-wrap;margin:0'>{}</pre>",
            escape_html(&text)
        );
    }
    if mimetype.starts_with("multipart/") {
        let mut plain: Option<String> = None;
        for sub in &part.subparts {
            let sub_type = sub.ctype.mimetype.to_ascii_lowercase();
            if sub_type == "text/html" {
                return extract_body(sub);
            }
            if sub_type == "text/plain" && plain.is_none() {
                plain = Some(extract_body(sub));
            }
            if sub_type.starts_with("multipart/") {
                let nested = extract_body(sub);
                if !nested.is_empty() {
                    return nested;
                }
            }
        }
        return plain.unwrap_or_default();
    }
    "<em style='color:#888'>(No readable content)</em>".to_string()
}

fn summary_from_raw(message_number: u32, is_read: bool, raw: &[u8]) -> InboxSummary {
    match mailparse::parse_mail(raw) {
        Ok(parsed) => InboxSummary {
            message_number,
            from: header_value(&parsed, "From", "(unknown sender)"),
            subject: header_value(&parsed, "Subject", "(no subject)"),
            date: header_value(&parsed, "Date", ""),
            is_read,
        },
        Err(_) => InboxSummary {
            message_number,
            from: "(unknown sender)".to_string(),
            subject: "(no subject)".to_string(),
            date: String::new(),
            is_read,
        },
    }
}

fn list_messages_blocking(cfg: &Config) -> anyhow::Result<Vec<InboxSummary>> {
    let mut session = open_session(cfg)?;
    let mailbox = session.select("INBOX")?;
    if mailbox.exists == 0 {
        session.logout().ok();
        return Ok(vec![]);
    }

    let seq = format!("1:{}", mailbox.exists);
    // BODY.PEEK[] (not BODY[]) so merely listing the inbox never marks
    // messages as read — matches Java's Folder.READ_ONLY list.
    let fetches = session.fetch(&seq, "(FLAGS BODY.PEEK[])")?;
    let mut out = Vec::with_capacity(fetches.len());
    for fetch in fetches.iter() {
        let is_read = fetch
            .flags()
            .iter()
            .any(|f| matches!(f, imap::types::Flag::Seen));
        let raw = fetch.body().unwrap_or(&[]);
        out.push(summary_from_raw(fetch.message, is_read, raw));
    }
    session.logout().ok();
    out.reverse(); // newest first, matching Java's descending iteration
    Ok(out)
}

fn get_message_blocking(cfg: &Config, message_number: u32) -> anyhow::Result<InboxMessage> {
    let mut session = open_session(cfg)?;
    session.select("INBOX")?;
    // Plain BODY[] (not .PEEK) so the server marks \Seen as a side effect of
    // the fetch, same as Java relying on JavaMail's read-through-open
    // behavior — the explicit STORE below is belt-and-suspenders for servers
    // that don't set it implicitly.
    let seq = message_number.to_string();
    let fetches = session.fetch(&seq, "BODY[]")?;
    let fetch = fetches
        .iter()
        .next()
        .ok_or_else(|| anyhow::anyhow!("Message not found"))?;
    let raw = fetch.body().unwrap_or(&[]).to_vec();
    session.store(&seq, "+FLAGS (\\Seen)").ok();
    session.logout().ok();

    let parsed = mailparse::parse_mail(&raw)?;
    Ok(InboxMessage {
        message_number,
        from: header_value(&parsed, "From", "(unknown sender)"),
        subject: header_value(&parsed, "Subject", "(no subject)"),
        date: header_value(&parsed, "Date", ""),
        is_read: true,
        body: extract_body(&parsed),
    })
}

fn delete_message_blocking(cfg: &Config, message_number: u32) -> anyhow::Result<()> {
    let mut session = open_session(cfg)?;
    session.select("INBOX")?;
    let seq = message_number.to_string();
    session.store(&seq, "+FLAGS (\\Deleted)")?;
    session.expunge()?;
    session.logout().ok();
    Ok(())
}

#[derive(Clone)]
struct InReplyTo(String);
impl Header for InReplyTo {
    fn name() -> HeaderName {
        HeaderName::new_from_ascii_str("In-Reply-To")
    }
    fn parse(s: &str) -> Result<Self, Box<dyn std::error::Error + Send + Sync>> {
        Ok(InReplyTo(s.to_string()))
    }
    fn display(&self) -> HeaderValue {
        HeaderValue::new(Self::name(), self.0.clone())
    }
}

#[derive(Clone)]
struct References(String);
impl Header for References {
    fn name() -> HeaderName {
        HeaderName::new_from_ascii_str("References")
    }
    fn parse(s: &str) -> Result<Self, Box<dyn std::error::Error + Send + Sync>> {
        Ok(References(s.to_string()))
    }
    fn display(&self) -> HeaderValue {
        HeaderValue::new(Self::name(), self.0.clone())
    }
}

fn reply_blocking(cfg: &Config, message_number: u32, reply_body: &str) -> anyhow::Result<()> {
    let mut session = open_session(cfg)?;
    session.select("INBOX")?;
    let seq = message_number.to_string();
    let fetches = session.fetch(&seq, "BODY.PEEK[]")?;
    let raw = fetches
        .iter()
        .next()
        .and_then(|f| f.body())
        .ok_or_else(|| anyhow::anyhow!("Message not found"))?
        .to_vec();
    session.logout().ok();

    let parsed = mailparse::parse_mail(&raw)?;
    let to = header_value(&parsed, "From", "");
    if to.is_empty() {
        anyhow::bail!("Original message has no From address to reply to");
    }
    let original_subject = header_value(&parsed, "Subject", "");
    let subject = if original_subject.to_ascii_lowercase().starts_with("re:") {
        original_subject
    } else {
        format!("Re: {original_subject}")
    };
    let message_id = parsed
        .headers
        .iter()
        .find(|h| h.get_key_ref().eq_ignore_ascii_case("Message-ID"))
        .map(|h| h.get_value());

    let from_mailbox: lettre::message::Mailbox =
        format!("{} <{}>", cfg.from_name, cfg.from_email).parse()?;
    let to_mailbox: lettre::message::Mailbox = to.parse()?;

    let mut builder = lettre::Message::builder()
        .from(from_mailbox)
        .to(to_mailbox)
        .subject(subject)
        .header(lettre::message::header::ContentType::TEXT_PLAIN);
    if let Some(ref mid) = message_id {
        builder = builder.header(InReplyTo(mid.clone()));
        builder = builder.header(References(mid.clone()));
    }
    let email = builder.body(reply_body.to_string())?;

    // Local trusted relay (Postfix on the same host) — no auth/TLS needed,
    // matching Java's plain spring.mail.* SMTP config.
    let mailer = lettre::SmtpTransport::builder_dangerous(&cfg.smtp_host)
        .port(cfg.smtp_port)
        .build();
    lettre::Transport::send(&mailer, &email)?;
    Ok(())
}

pub async fn list_messages(cfg: Config) -> crate::error::Result<Vec<InboxSummary>> {
    tokio::task::spawn_blocking(move || list_messages_blocking(&cfg))
        .await
        .map_err(|e| AppError::Internal(format!("Mailbox task failed: {e}")))?
        .map_err(|e| AppError::Internal(format!("Failed to read inbox: {e}")))
}

pub async fn get_message(cfg: Config, message_number: u32) -> crate::error::Result<InboxMessage> {
    tokio::task::spawn_blocking(move || get_message_blocking(&cfg, message_number))
        .await
        .map_err(|e| AppError::Internal(format!("Mailbox task failed: {e}")))?
        .map_err(|e| AppError::Internal(format!("Failed to fetch message: {e}")))
}

pub async fn delete_message(cfg: Config, message_number: u32) -> crate::error::Result<()> {
    tokio::task::spawn_blocking(move || delete_message_blocking(&cfg, message_number))
        .await
        .map_err(|e| AppError::Internal(format!("Mailbox task failed: {e}")))?
        .map_err(|e| AppError::Internal(format!("Failed to delete message: {e}")))
}

pub async fn reply_to_message(
    cfg: Config,
    message_number: u32,
    body: String,
) -> crate::error::Result<()> {
    tokio::task::spawn_blocking(move || reply_blocking(&cfg, message_number, &body))
        .await
        .map_err(|e| AppError::Internal(format!("Mailbox task failed: {e}")))?
        .map_err(|e| AppError::Internal(format!("Failed to send reply: {e}")))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn raw_email(headers: &str, body: &str) -> Vec<u8> {
        format!("{headers}\r\n\r\n{body}").into_bytes()
    }

    #[test]
    fn summary_extracts_from_subject_date() {
        let raw = raw_email(
            "From: Jane Doe <jane@example.com>\r\nSubject: Hello there\r\nDate: Mon, 1 Jan 2026 10:00:00 +0000",
            "Hi",
        );
        let s = summary_from_raw(3, false, &raw);
        assert_eq!(s.message_number, 3);
        assert!(s.from.contains("jane@example.com"));
        assert_eq!(s.subject, "Hello there");
        assert!(!s.is_read);
    }

    #[test]
    fn summary_falls_back_when_headers_missing() {
        let raw = raw_email("X-Custom: value", "body");
        let s = summary_from_raw(1, true, &raw);
        assert_eq!(s.from, "(unknown sender)");
        assert_eq!(s.subject, "(no subject)");
        assert!(s.is_read);
    }

    #[test]
    fn summary_handles_unparseable_bytes() {
        let s = summary_from_raw(2, false, b"\xff\xfe not an email");
        assert_eq!(s.from, "(unknown sender)");
        assert_eq!(s.subject, "(no subject)");
    }

    #[test]
    fn extract_body_prefers_plain_text_wrapped_in_pre() {
        let raw = raw_email("Content-Type: text/plain", "line one <b>not html</b>");
        let parsed = mailparse::parse_mail(&raw).unwrap();
        let body = extract_body(&parsed);
        assert!(body.starts_with("<pre"));
        assert!(body.contains("&lt;b&gt;"));
    }

    #[test]
    fn extract_body_uses_html_directly() {
        let raw = raw_email("Content-Type: text/html", "<p>hello</p>");
        let parsed = mailparse::parse_mail(&raw).unwrap();
        assert_eq!(extract_body(&parsed), "<p>hello</p>");
    }

    #[test]
    fn extract_body_unreadable_type_gives_placeholder() {
        let raw = raw_email("Content-Type: application/octet-stream", "binary junk");
        let parsed = mailparse::parse_mail(&raw).unwrap();
        assert!(extract_body(&parsed).contains("No readable content"));
    }
}
