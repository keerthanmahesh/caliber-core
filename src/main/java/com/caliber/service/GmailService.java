package com.caliber.service;

import com.caliber.model.GmailActionLabel;
import com.caliber.model.GmailEmploymentLabel;
import com.caliber.model.JobEmail;
import com.caliber.model.ResumeDocument;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.ListLabelsResponse;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.google.api.services.gmail.model.ModifyMessageRequest;
import com.google.api.services.gmail.model.ModifyThreadRequest;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static com.caliber.constant.AppConstants.EMPTY_STRING;
import com.caliber.constant.AppConstants;

/**
 * Service managing Gmail message querying, MIME decoding,
 * thread-preserving replies with resume attachments, and label management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GmailService {

    private final GmailAuthService gmailAuthService;

    // Cache of Gmail label IDs by label name
    private final Map<String, String> labelIdCache = new ConcurrentHashMap<>();

    /**
     * Fetches message stubs matching the configured query.
     */
    public List<Message> listMessages(String userId, String query, long maxResults) throws IOException, GeneralSecurityException {
        Gmail gmail = gmailAuthService.getGmailClient(userId);
        ListMessagesResponse response = gmail.users().messages().list(AppConstants.GMAIL_USER_ME)
                .setQ(query)
                .setMaxResults(maxResults)
                .execute();

        return response.getMessages() != null ? response.getMessages() : Collections.emptyList();
    }

    /**
     * Retrieves full message details including headers and payload.
     */
    public Message getMessage(String userId, String messageId) throws IOException, GeneralSecurityException {
        Gmail gmail = gmailAuthService.getGmailClient(userId);
        return gmail.users().messages().get(AppConstants.GMAIL_USER_ME, messageId)
                .setFormat("full")
                .execute();
    }

    /**
     * Sends a thread-preserving reply message, optionally attaching a resume,
     * and applies the respective Gmail label while archiving from Inbox if configured.
     */
    public String sendReply(String userId, JobEmail originalEmail, String replySubject,
                            String replyBody, ResumeDocument resumeToAttach, String targetLabelName,
                            boolean archiveInbox) throws GeneralSecurityException, IOException, MessagingException {

        Gmail gmail = gmailAuthService.getGmailClient(userId);

        // 1. Compose MIME Message with strict thread integrity headers
        MimeMessage mimeMessage = composeReplyMimeMessage(originalEmail, replySubject, replyBody, resumeToAttach);

        // 2. Base64URL encode and send via Gmail API
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        mimeMessage.writeTo(buffer);
        byte[] bytes = buffer.toByteArray();
        String encodedEmail = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Message message = new Message();
        message.setRaw(encodedEmail);
        message.setThreadId(originalEmail.getThreadId());

        Message sentMessage = gmail.users().messages().send(AppConstants.GMAIL_USER_ME, message).execute();
        log.info("Successfully sent reply for thread {} with Message-ID {}", originalEmail.getThreadId(), sentMessage.getId());

        // 3. Apply Gmail Labels, Inbox Removal, and Mark as Important
        try {
            applyLabelAndArchive(gmail, originalEmail.getMessageId(), originalEmail.getThreadId(), targetLabelName, archiveInbox, true);
        } catch (Exception e) {
            log.warn("Failed to update Gmail labels for message {}: {}", originalEmail.getMessageId(), e.getMessage());
        }

        return sentMessage.getId();
    }

    /**
     * Composes MimeMessage respecting Thread Preservation Headers:
     * - threadId
     * - In-Reply-To
     * - References
     * - Subject (prepends 'Re: ')
     */
    public MimeMessage composeReplyMimeMessage(JobEmail originalEmail, String subject,
                                              String body, ResumeDocument resume) throws MessagingException {

        Session session = Session.getDefaultInstance(System.getProperties(), null);
        MimeMessage mimeMessage = new MimeMessage(session);

        mimeMessage.addRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(originalEmail.getSenderEmail()));
        mimeMessage.setSubject(resolveReplySubject(subject, originalEmail.getSubject()));
        applyThreadHeaders(mimeMessage, originalEmail.getMessageId());
        setReplyContent(mimeMessage, body, resume);

        mimeMessage.setSentDate(new Date());
        mimeMessage.saveChanges();
        return mimeMessage;
    }

    private String resolveReplySubject(String subject, String fallbackSubject) {
        String formatted = (subject != null && !subject.isBlank()) ? subject : fallbackSubject;
        if (formatted == null || formatted.isBlank()) {
            return "Re: No Subject";
        }
        if (!formatted.toLowerCase().startsWith("re:")) {
            return "Re: " + formatted;
        }
        return formatted;
    }

    private void applyThreadHeaders(MimeMessage mimeMessage, String messageId) throws MessagingException {
        if (messageId == null || messageId.isBlank()) {
            return;
        }
        String cleanMessageId = messageId.trim();
        if (!cleanMessageId.startsWith("<")) {
            cleanMessageId = "<" + cleanMessageId;
        }
        if (!cleanMessageId.endsWith(">")) {
            cleanMessageId = cleanMessageId + ">";
        }
        mimeMessage.setHeader("In-Reply-To", cleanMessageId);
        mimeMessage.setHeader("References", cleanMessageId);
    }

    private void setReplyContent(MimeMessage mimeMessage, String body, ResumeDocument resume) throws MessagingException {
        if (resume != null && resume.getData() != null && resume.getData().length > 0) {
            mimeMessage.setContent(buildMultipartWithResume(body, resume));
        } else {
            mimeMessage.setText(body, StandardCharsets.UTF_8.name());
        }
    }

    private Multipart buildMultipartWithResume(String body, ResumeDocument resume) throws MessagingException {
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(body, StandardCharsets.UTF_8.name());

        MimeBodyPart attachmentPart = new MimeBodyPart();
        String contentType = resume.getContentType() != null ? resume.getContentType() : "application/pdf";
        DataSource source = new ByteArrayDataSource(resume.getData(), contentType);
        attachmentPart.setDataHandler(new DataHandler(source));
        attachmentPart.setFileName(resume.getFilename() != null ? resume.getFilename() : "Resume.pdf");

        Multipart multipart = new MimeMultipart("mixed");
        multipart.addBodyPart(textPart);
        multipart.addBodyPart(attachmentPart);
        return multipart;
    }

    /**
     * Applies target label and optionally removes INBOX from Gmail thread and message.
     * Ensures mutually exclusive labels (e.g. C2C vs W2 vs Unspecified) are cleanly replaced
     * rather than stacked.
     */
    public void applyLabelAndArchive(Gmail gmail, String messageId, String threadId, String targetLabelName, boolean archiveInbox) throws IOException {
        applyLabelAndArchive(gmail, messageId, threadId, targetLabelName, archiveInbox, false);
    }

    public void applyLabelAndArchive(Gmail gmail, String messageId, String threadId, String targetLabelName, boolean archiveInbox, boolean markImportant) throws IOException {
        String targetLabelId = getOrCreateLabel(gmail, targetLabelName);

        List<String> addLabels = new ArrayList<>();
        if (targetLabelId != null) {
            addLabels.add(targetLabelId);
        }
        if (markImportant) {
            addLabels.add(AppConstants.GMAIL_LABEL_IMPORTANT);
        }

        List<String> removeLabels = new ArrayList<>();
        if (archiveInbox) {
            removeLabels.add(AppConstants.GMAIL_LABEL_INBOX);
        }

        collectConflictingEmploymentLabels(gmail, targetLabelName, removeLabels);
        collectConflictingActionLabels(gmail, targetLabelName, removeLabels);

        executeLabelModifications(gmail, messageId, threadId, addLabels, removeLabels);
    }

    private void collectConflictingEmploymentLabels(Gmail gmail, String targetLabelName, List<String> removeLabels) {
        if (!GmailEmploymentLabel.isEmploymentLabel(targetLabelName)) {
            return;
        }
        for (GmailEmploymentLabel empLabel : GmailEmploymentLabel.values()) {
            if (!empLabel.getLabelValue().equalsIgnoreCase(targetLabelName)) {
                addExistingLabelIfPresent(gmail, empLabel.getLabelValue(), removeLabels);
            }
        }
    }

    private void collectConflictingActionLabels(Gmail gmail, String targetLabelName, List<String> removeLabels) {
        if (targetLabelName == null) {
            return;
        }
        if (GmailActionLabel.DISMISSED.getLabelValue().equalsIgnoreCase(targetLabelName)) {
            addExistingLabelIfPresent(gmail, GmailActionLabel.INQUIRED.getLabelValue(), removeLabels);
            addExistingLabelIfPresent(gmail, GmailActionLabel.APPLIED.getLabelValue(), removeLabels);
        } else if (isActionLabel(targetLabelName)) {
            addExistingLabelIfPresent(gmail, GmailActionLabel.DISMISSED.getLabelValue(), removeLabels);
        }
    }

    private boolean isActionLabel(String labelName) {
        return GmailActionLabel.INQUIRED.getLabelValue().equalsIgnoreCase(labelName)
                || GmailActionLabel.APPLIED.getLabelValue().equalsIgnoreCase(labelName);
    }

    private void addExistingLabelIfPresent(Gmail gmail, String labelName, List<String> targetList) {
        String existingId = getExistingLabelId(gmail, labelName);
        if (existingId != null && !targetList.contains(existingId)) {
            targetList.add(existingId);
        }
    }

    private void executeLabelModifications(Gmail gmail, String messageId, String threadId,
                                           List<String> addLabels, List<String> removeLabels) throws IOException {
        if (addLabels.isEmpty() && removeLabels.isEmpty()) {
            return;
        }

        if (threadId != null && !threadId.isBlank() && tryModifyThread(gmail, threadId, addLabels, removeLabels)) {
            return;
        }

        if (messageId != null && !messageId.isBlank()) {
            modifyMessageLabels(gmail, messageId, addLabels, removeLabels);
        }
    }

    private boolean tryModifyThread(Gmail gmail, String threadId, List<String> addLabels, List<String> removeLabels) {
        try {
            ModifyThreadRequest modifyThreadRequest = new ModifyThreadRequest()
                    .setAddLabelIds(addLabels)
                    .setRemoveLabelIds(removeLabels);
            gmail.users().threads().modify(AppConstants.GMAIL_USER_ME, threadId, modifyThreadRequest).execute();
            log.info("Modified Gmail thread {}: added {} removed {}", threadId, addLabels, removeLabels);
            return true;
        } catch (Exception e) {
            log.warn("Failed to modify Gmail thread {}, falling back to message modify: {}", threadId, e.getMessage());
            return false;
        }
    }

    private void modifyMessageLabels(Gmail gmail, String messageId, List<String> addLabels, List<String> removeLabels) throws IOException {
        ModifyMessageRequest modifyRequest = new ModifyMessageRequest()
                .setAddLabelIds(addLabels)
                .setRemoveLabelIds(removeLabels);
        gmail.users().messages().modify(AppConstants.GMAIL_USER_ME, messageId, modifyRequest).execute();
        log.info("Modified Gmail message {}: added {} removed {}", messageId, addLabels, removeLabels);
    }

    /**
     * Resolves the label ID if it exists in Gmail or cache, but does NOT create a new label.
     */
    public synchronized String getExistingLabelId(Gmail gmail, String labelName) {
        if (labelName == null || labelName.isBlank()) return null;

        if (labelIdCache.containsKey(labelName)) {
            return labelIdCache.get(labelName);
        }

        try {
            ListLabelsResponse response = gmail.users().labels().list(AppConstants.GMAIL_USER_ME).execute();
            if (response.getLabels() != null) {
                for (Label label : response.getLabels()) {
                    if (label.getName() != null && label.getId() != null) {
                        labelIdCache.put(label.getName(), label.getId());
                    }
                }
            }
            return labelIdCache.get(labelName);
        } catch (Exception e) {
            log.warn("Could not check existing label '{}': {}", labelName, e.getMessage());
            return null;
        }
    }

    /**
     * Inspects Gmail label IDs on a message and resolves which Jobs/* employment label is already present.
     */
    public synchronized GmailEmploymentLabel getExistingEmploymentLabel(Gmail gmail, List<String> messageLabelIds) {
        if (messageLabelIds == null || messageLabelIds.isEmpty()) return null;

        for (GmailEmploymentLabel empLabel : GmailEmploymentLabel.values()) {
            String labelId = getExistingLabelId(gmail, empLabel.getLabelValue());
            if (labelId != null && messageLabelIds.contains(labelId)) {
                return empLabel;
            }
        }
        return null;
    }

    /**
     * Inspects Gmail label IDs on a message and resolves which Jobs/* action label is already present.
     */
    public synchronized GmailActionLabel getExistingActionLabel(Gmail gmail, List<String> messageLabelIds) {
        if (messageLabelIds == null || messageLabelIds.isEmpty()) return null;

        for (GmailActionLabel actLabel : List.of(GmailActionLabel.INQUIRED, GmailActionLabel.APPLIED, GmailActionLabel.DISMISSED)) {
            String labelId = getExistingLabelId(gmail, actLabel.getLabelValue());
            if (labelId != null && messageLabelIds.contains(labelId)) {
                return actLabel;
            }
        }
        return null;
    }

    /**
     * Gets or creates a Gmail label by display name.
     */
    public synchronized String getOrCreateLabel(Gmail gmail, String labelName) {
        if (labelName == null || labelName.isBlank()) return null;

        if (labelIdCache.containsKey(labelName)) {
            return labelIdCache.get(labelName);
        }

        try {
            ListLabelsResponse response = gmail.users().labels().list(AppConstants.GMAIL_USER_ME).execute();
            if (response.getLabels() != null) {
                for (Label label : response.getLabels()) {
                    if (label.getName() != null && label.getId() != null) {
                        labelIdCache.put(label.getName(), label.getId());
                    }
                }
            }

            if (labelIdCache.containsKey(labelName)) {
                return labelIdCache.get(labelName);
            }

            // Label does not exist, create it
            Label newLabel = new Label()
                    .setName(labelName)
                    .setLabelListVisibility("labelShow")
                    .setMessageListVisibility("show");

            Label created = gmail.users().labels().create(AppConstants.GMAIL_USER_ME, newLabel).execute();
            log.info("Created Gmail label '{}' with id {}", labelName, created.getId());
            labelIdCache.put(labelName, created.getId());
            return created.getId();
        } catch (Exception e) {
            log.warn("Could not find or create label '{}': {}", labelName, e.getMessage());
            return null;
        }
    }

    /**
     * Helper to extract header value from Gmail MessagePartHeaders.
     */
    public String getHeaderValue(List<MessagePartHeader> headers, String headerName) {
        if (headers == null) return null;
        for (MessagePartHeader header : headers) {
            if (headerName.equalsIgnoreCase(header.getName())) {
                return header.getValue();
            }
        }
        return null;
    }

    private static final Pattern CSS_IMPORT_PATTERN = Pattern.compile("(?is)@import\\s+(?:url\\([^)]*\\)|['\"][^'\"]*['\"])\\s*;?");
    private static final Pattern CSS_MEDIA_PATTERN = Pattern.compile("(?is)@media[^{]*\\{(?:[^{}]*\\{[^{}]*\\}[^{}]*|[^{}]*)*\\}");
    private static final Pattern CSS_RULE_BLOCK_PATTERN = Pattern.compile("(?is)(?:^|[\\r\\n\\s])[.#a-zA-Z0-9_\\-*][^{}\\r\\n;]*?\\{[^}]*\\}");
    private static final Pattern RESIDUAL_CSS_PROP_PATTERN = Pattern.compile("(?is)(?:-webkit-|-moz-|-ms-)?[a-zA-Z\\-]+\\s*:\\s*[^;{}]+\\s*;");
    private static final Pattern MULTI_NEWLINE_PATTERN = Pattern.compile("(?m)\\n{3,}");
    private static final Pattern TRAILING_SPACE_PER_LINE = Pattern.compile("(?m)[ \\t]+$");
    private static final Pattern REGEX_HEAD = Pattern.compile("(?is)<head[^>]*>.*?</head>");
    private static final Pattern REGEX_STYLE = Pattern.compile("(?is)<style[^>]*>.*?</style>");
    private static final Pattern REGEX_SCRIPT = Pattern.compile("(?is)<script[^>]*>.*?</script>");
    private static final Pattern BR_PATTERN = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern BLOCK_TAG_PATTERN = Pattern.compile("(?i)</?(?:p|div|tr|h[1-6]|table|blockquote)\\b[^>]*>");
    private static final Pattern LI_PATTERN = Pattern.compile("(?i)<li\\b[^>]*>");
    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");

    /**
     * Recursively extracts plain text content from MIME parts.
     */
    public String extractPlainText(MessagePart part) {
        if (part == null) return EMPTY_STRING;
        if (AppConstants.MIME_TEXT_PLAIN.equalsIgnoreCase(part.getMimeType()) && part.getBody() != null && part.getBody().getData() != null) {
            return cleanPlainText(decodeBase64Url(part.getBody().getData()));
        }

        if (part.getParts() != null) {
            String extracted = findPlainTextSubpart(part.getParts());
            if (!extracted.isBlank()) {
                return extracted;
            }
        }

        if (AppConstants.MIME_TEXT_HTML.equalsIgnoreCase(part.getMimeType()) && part.getBody() != null && part.getBody().getData() != null) {
            String html = decodeBase64Url(part.getBody().getData());
            return stripHtml(html);
        }

        return EMPTY_STRING;
    }

    private String findPlainTextSubpart(List<MessagePart> parts) {
        for (MessagePart subPart : parts) {
            if (AppConstants.MIME_TEXT_PLAIN.equalsIgnoreCase(subPart.getMimeType())) {
                String subText = extractPlainText(subPart);
                if (!subText.isBlank()) return subText;
            }
        }
        for (MessagePart subPart : parts) {
            if (subPart.getParts() != null) {
                String subText = extractPlainText(subPart);
                if (!subText.isBlank()) return subText;
            }
        }
        for (MessagePart subPart : parts) {
            if (AppConstants.MIME_TEXT_HTML.equalsIgnoreCase(subPart.getMimeType())) {
                String subText = extractPlainText(subPart);
                if (!subText.isBlank()) return subText;
            }
        }
        return EMPTY_STRING;
    }

    /**
     * Recursively extracts HTML content from MIME parts.
     */
    public String extractHtml(MessagePart part) {
        if (part == null) return EMPTY_STRING;
        if (AppConstants.MIME_TEXT_HTML.equalsIgnoreCase(part.getMimeType()) && part.getBody() != null && part.getBody().getData() != null) {
            return decodeBase64Url(part.getBody().getData());
        }

        if (part.getParts() != null) {
            for (MessagePart subPart : part.getParts()) {
                String subHtml = extractHtml(subPart);
                if (!subHtml.isBlank()) return subHtml;
            }
        }

        return EMPTY_STRING;
    }

    private String decodeBase64Url(String base64Url) {
        if (base64Url == null || base64Url.isBlank()) {
            return EMPTY_STRING;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(base64Url);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            log.error("Failed to decode Base64URL string: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid Base64URL content: " + e.getMessage(), e);
        }
    }

    /**
     * Strips HTML markup, removes script/style blocks, and formats readable plain text.
     */
    public String stripHtml(String html) {
        if (html == null || html.isBlank()) return EMPTY_STRING;
        try {
            Document doc = Jsoup.parse(html);
            doc.select("head, style, script, noscript, svg, xml, iframe, link, meta").remove();

            for (Element br : doc.select("br")) {
                br.replaceWith(new TextNode("\n"));
            }
            for (Element p : doc.select("p, div, tr, h1, h2, h3, h4, h5, h6")) {
                p.prepend("\n");
            }
            for (Element li : doc.select("li")) {
                li.prepend("\n• ");
            }
            for (Element td : doc.select("td, th")) {
                td.append("  ");
            }

            String text = doc.body() != null ? doc.body().wholeText() : doc.wholeText();
            return cleanPlainText(text);
        } catch (Exception e) {
            log.warn("Jsoup parsing failed, falling back to regex: {}", e.getMessage());
            return cleanWithRegex(html);
        }
    }

    private String cleanWithRegex(String html) {
        if (html == null) return EMPTY_STRING;
        String text = REGEX_HEAD.matcher(html).replaceAll(EMPTY_STRING);
        text = REGEX_STYLE.matcher(text).replaceAll(EMPTY_STRING);
        text = REGEX_SCRIPT.matcher(text).replaceAll(EMPTY_STRING);
        text = BR_PATTERN.matcher(text).replaceAll("\n");
        text = BLOCK_TAG_PATTERN.matcher(text).replaceAll("\n");
        text = LI_PATTERN.matcher(text).replaceAll("\n• ");
        text = TAG_PATTERN.matcher(text).replaceAll(" ");
        return cleanPlainText(text);
    }

    /**
     * Cleans plain text by stripping residual CSS definitions, media queries,
     * unescaped HTML entities, and excessive whitespace.
     */
    public String cleanPlainText(String text) {
        if (text == null || text.isBlank()) {
            return EMPTY_STRING;
        }

        String cleaned = text;
        cleaned = CSS_IMPORT_PATTERN.matcher(cleaned).replaceAll(EMPTY_STRING);
        cleaned = CSS_MEDIA_PATTERN.matcher(cleaned).replaceAll(EMPTY_STRING);

        int prevLength = -1;
        int maxPasses = 5;
        while (maxPasses-- > 0 && cleaned.length() != prevLength && cleaned.contains("{") && cleaned.contains("}")) {
            prevLength = cleaned.length();
            cleaned = CSS_RULE_BLOCK_PATTERN.matcher(cleaned).replaceAll(" ");
        }

        cleaned = RESIDUAL_CSS_PROP_PATTERN.matcher(cleaned).replaceAll(EMPTY_STRING);

        cleaned = cleaned.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");

        cleaned = TRAILING_SPACE_PER_LINE.matcher(cleaned).replaceAll(EMPTY_STRING);
        cleaned = MULTI_NEWLINE_PATTERN.matcher(cleaned).replaceAll("\n\n");

        return cleaned.trim();
    }
}
