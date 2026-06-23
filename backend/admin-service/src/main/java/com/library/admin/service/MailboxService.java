package com.library.admin.service;

import com.library.admin.dto.InboxMessageDto;
import com.library.admin.dto.InboxSummaryDto;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Service
@Slf4j
@RequiredArgsConstructor
public class MailboxService {

    private final JavaMailSender mailSender;

    @Value("${admin.inbox.imap-host:localhost}")  private String  imapHost;
    @Value("${admin.inbox.imap-port:143}")         private int     imapPort;
    @Value("${admin.inbox.username:admin}")        private String  username;
    @Value("${admin.inbox.password:}")             private String  password;
    @Value("${admin.inbox.use-ssl:false}")         private boolean useSsl;
    @Value("${admin.inbox.from-email:admin@targetzone.co.in}") private String fromEmail;

    private Store openStore() throws MessagingException {
        String protocol = useSsl ? "imaps" : "imap";
        Properties props = new Properties();
        props.put("mail." + protocol + ".host", imapHost);
        props.put("mail." + protocol + ".port", String.valueOf(imapPort));
        if (useSsl) props.put("mail.imaps.ssl.enable", "true");
        Session session = Session.getInstance(props);
        Store store = session.getStore(protocol);
        store.connect(imapHost, imapPort, username, password);
        return store;
    }

    public List<InboxSummaryDto> listMessages() {
        try (Store store = openStore()) {
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);
            Message[] messages = inbox.getMessages();
            List<InboxSummaryDto> result = new ArrayList<>();
            for (int i = messages.length - 1; i >= 0; i--) {
                Message msg = messages[i];
                result.add(InboxSummaryDto.builder()
                        .messageNumber(msg.getMessageNumber())
                        .from(extractFrom(msg))
                        .subject(msg.getSubject() != null ? msg.getSubject() : "(no subject)")
                        .date(msg.getSentDate() != null ? msg.getSentDate().toString() : "")
                        .isRead(msg.isSet(Flags.Flag.SEEN))
                        .build());
            }
            inbox.close(false);
            return result;
        } catch (Exception e) {
            log.error("Failed to list inbox messages", e);
            throw new RuntimeException("Failed to read inbox: " + e.getMessage());
        }
    }

    public InboxMessageDto getMessage(int messageNumber) {
        try (Store store = openStore()) {
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);
            Message msg = inbox.getMessage(messageNumber);
            String body = extractBody(msg);
            msg.setFlag(Flags.Flag.SEEN, true);
            InboxMessageDto dto = InboxMessageDto.builder()
                    .messageNumber(msg.getMessageNumber())
                    .from(extractFrom(msg))
                    .subject(msg.getSubject() != null ? msg.getSubject() : "(no subject)")
                    .date(msg.getSentDate() != null ? msg.getSentDate().toString() : "")
                    .isRead(true)
                    .body(body)
                    .build();
            inbox.close(true);
            return dto;
        } catch (Exception e) {
            log.error("Failed to fetch message #{}", messageNumber, e);
            throw new RuntimeException("Failed to fetch message: " + e.getMessage());
        }
    }

    public void deleteMessage(int messageNumber) {
        try (Store store = openStore()) {
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);
            inbox.getMessage(messageNumber).setFlag(Flags.Flag.DELETED, true);
            inbox.close(true);
        } catch (Exception e) {
            log.error("Failed to delete message #{}", messageNumber, e);
            throw new RuntimeException("Failed to delete message: " + e.getMessage());
        }
    }

    public void replyToMessage(int messageNumber, String replyBody) {
        try (Store store = openStore()) {
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            Message original = inbox.getMessage(messageNumber);

            String to         = extractFrom(original);
            String origSubj   = original.getSubject() != null ? original.getSubject() : "";
            String subject    = origSubj.startsWith("Re:") ? origSubj : "Re: " + origSubj;
            String[] msgIds   = original.getHeader("Message-ID");
            String inReplyTo  = (msgIds != null && msgIds.length > 0) ? msgIds[0] : null;
            inbox.close(false);

            MimeMessage reply = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(reply, false, "UTF-8");
            h.setFrom(fromEmail);
            h.setTo(to);
            h.setSubject(subject);
            h.setText(replyBody, false);
            if (inReplyTo != null) {
                reply.setHeader("In-Reply-To", inReplyTo);
                reply.setHeader("References", inReplyTo);
            }
            mailSender.send(reply);
        } catch (Exception e) {
            log.error("Failed to reply to message #{}", messageNumber, e);
            throw new RuntimeException("Failed to send reply: " + e.getMessage());
        }
    }

    private String extractFrom(Message msg) {
        try {
            Address[] from = msg.getFrom();
            if (from != null && from.length > 0) return from[0].toString();
        } catch (MessagingException e) {
            log.warn("Could not extract From header", e);
        }
        return "(unknown sender)";
    }

    private String extractBody(Part part) throws MessagingException, IOException {
        if (part.isMimeType("text/html")) {
            return (String) part.getContent();
        }
        if (part.isMimeType("text/plain")) {
            String text = (String) part.getContent();
            return "<pre style='font-family:inherit;white-space:pre-wrap;margin:0'>" + escapeHtml(text) + "</pre>";
        }
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            String plain = null;
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart bp = mp.getBodyPart(i);
                if (bp.isMimeType("text/html")) return extractBody(bp);
                if (bp.isMimeType("text/plain") && plain == null) plain = extractBody(bp);
                if (bp.isMimeType("multipart/*")) {
                    String nested = extractBody(bp);
                    if (nested != null) return nested;
                }
            }
            return plain;
        }
        return "<em style='color:#888'>(No readable content)</em>";
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
