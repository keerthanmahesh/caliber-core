package com.caliber.constant;

import com.caliber.model.ApplicationStatus;
import java.util.List;

/**
 * Central application constants for Caliber Core.
 */
public final class AppConstants {

    private AppConstants() {
        // Prevent instantiation
    }

    // =========================================================================
    // Auth & Identity Constants
    // =========================================================================

    public static final String ANONYMOUS_USER = "anonymousUser";
    public static final String DEFAULT_USER_FIRST_NAME = "User";
    public static final String EMPTY_STRING = "";

    // =========================================================================
    // MongoDB Collection Names
    // =========================================================================

    public static final String COLLECTION_USER = "user";
    public static final String COLLECTION_USER_SETTING = "user_setting";
    public static final String COLLECTION_JOB_EMAIL = "job_email";
    public static final String COLLECTION_RESUME_DOCUMENT = "resume_document";

    // =========================================================================
    // Gmail & Google OAuth2 Defaults
    // =========================================================================

    public static final String GMAIL_USER_ME = "me";
    public static final String GMAIL_LABEL_IMPORTANT = "IMPORTANT";
    public static final String GMAIL_LABEL_INBOX = "INBOX";

    public static final String DEFAULT_GMAIL_SCOPE = "https://www.googleapis.com/auth/gmail.modify";
    public static final String DEFAULT_GMAIL_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    public static final String DEFAULT_GMAIL_TOKEN_URL = "https://oauth2.googleapis.com";
    public static final long DEFAULT_GMAIL_TOKEN_EXPIRY_SECONDS = 3600L;

    // OAuth2 Query & Form Parameters
    public static final String OAUTH_PARAM_CLIENT_ID = com.security.core.constant.SecurityConstants.CLIENT_ID;
    public static final String OAUTH_PARAM_CLIENT_SECRET = "client_secret";
    public static final String OAUTH_PARAM_REDIRECT_URI = "redirect_uri";
    public static final String OAUTH_PARAM_RESPONSE_TYPE = "response_type";
    public static final String OAUTH_PARAM_SCOPE = "scope";
    public static final String OAUTH_PARAM_ACCESS_TYPE = "access_type";
    public static final String OAUTH_PARAM_PROMPT = "prompt";
    public static final String OAUTH_PARAM_CODE = "code";
    public static final String OAUTH_PARAM_GRANT_TYPE = "grant_type";
    public static final String OAUTH_PARAM_REFRESH_TOKEN = "refresh_token";

    // OAuth2 Parameter Values
    public static final String OAUTH_VALUE_CODE = "code";
    public static final String OAUTH_VALUE_OFFLINE = "offline";
    public static final String OAUTH_VALUE_CONSENT = "consent";
    public static final String OAUTH_VALUE_AUTHORIZATION_CODE = "authorization_code";
    public static final String OAUTH_VALUE_REFRESH_TOKEN = "refresh_token";

    // OAuth2 Endpoints & Token JSON Response Fields
    public static final String OAUTH_TOKEN_PATH = "/token";
    public static final String OAUTH_FIELD_REFRESH_TOKEN = "refresh_token";
    public static final String OAUTH_FIELD_ACCESS_TOKEN = "access_token";
    public static final String OAUTH_FIELD_EXPIRES_IN = "expires_in";

    // Delimiters
    public static final String QUESTION_MARK = "?";
    public static final String AMPERSAND = "&";
    public static final String EQUALS = "=";

    // Response Map Keys
    public static final String KEY_AUTH_URL = "authUrl";
    public static final String KEY_SUCCESS = "success";
    public static final String KEY_MESSAGE = "message";
    public static final String KEY_CONNECTED = "connected";
    public static final String KEY_CLIENT_ID = "clientId";
    public static final String KEY_SEARCH_QUERY = "searchQuery";
    public static final String KEY_REDIRECT_URI = "redirectUri";
    public static final String KEY_CODE = "code";

    // OAuth & AI User Feedback Messages
    public static final String MSG_GMAIL_CONNECTED = "Gmail successfully connected!";
    public static final String MSG_AI_TEST_SUCCEEDED = "AI extraction test succeeded!";
    public static final String ERR_CLIENT_ID_NOT_CONFIGURED = "Gmail Client ID is not configured.";
    public static final String ERR_AUTH_CODE_REQUIRED = "Authorization code is required";
    public static final String ERR_OAUTH_CREDENTIALS_NOT_CONFIGURED = "Gmail OAuth credentials (Client ID and Client Secret) are not configured.";
    public static final String ERR_TOKEN_EXCHANGE_FAILED_PREFIX = "Failed to exchange authorization code: ";
    public static final String ERR_TOKEN_REFRESH_FAILED_PREFIX = "Could not refresh Gmail OAuth token: ";
    public static final String ERR_AI_TEST_FAILED_PREFIX = "AI extraction failed: ";
    public static final String ERR_AI_NOT_CONFIGURED = "Neither OLLAMA nor GEMINI is configured.";
    public static final String ERR_AI_EXTRACTION_FAILED_PREFIX = "Both primary (Ollama) and fallback (Gemini) failed: ";

    // =========================================================================
    // AI Engine Configuration Defaults
    // =========================================================================

    public static final int DEFAULT_MAX_BODY_CHARS = 10000;

    // =========================================================================
    // Job & Email Query Constants
    // =========================================================================

    public static final String FIELD_RECEIVED_AT = "receivedAt";
    public static final String DEFAULT_PAGE_NUMBER = "0";
    public static final String DEFAULT_PAGE_SIZE = "20";

    // Request Parameter Names
    public static final String PARAM_TAB = "tab";
    public static final String PARAM_TYPE = "type";
    public static final String PARAM_STATUS = "status";
    public static final String PARAM_SEARCH = "search";
    public static final String PARAM_PAGE = "page";
    public static final String PARAM_SIZE = "size";

    // Tab Names
    public static final String TAB_C2C = "c2c";
    public static final String TAB_CONFIRMED = "confirmed";
    public static final String TAB_C2H = "c2h";
    public static final String TAB_W2 = "w2";
    public static final String TAB_FULL_TIME = "full_time";
    public static final String TAB_UNSPECIFIED = "unspecified";
    public static final String TAB_HISTORY = "history";
    public static final String TAB_ACTED = "acted";

    // Status Filter Values
    public static final String STATUS_ALL = "ALL";
    public static final String STATUS_ACTED = "ACTED";
    public static final List<ApplicationStatus> ACTED_STATUSES = List.of(
            ApplicationStatus.INQUIRED,
            ApplicationStatus.APPLIED,
            ApplicationStatus.DISMISSED
    );

    // Reply & Draft Types
    public static final String REPLY_TYPE_APPLY = "APPLY";
    public static final String REPLY_TYPE_INQUIRY = "INQUIRY";
    public static final String DEFAULT_CANDIDATE_NAME = "Candidate";
    public static final String RE_PREFIX = "Re: ";
    public static final String RE_PREFIX_LOWER = "re:";

    // MIME Types
    public static final String MIME_TEXT_PLAIN = "text/plain";
    public static final String MIME_TEXT_HTML = "text/html";
    public static final String MIME_TYPE_TEXT_PLAIN = MIME_TEXT_PLAIN;
    public static final String MIME_TYPE_TEXT_HTML = MIME_TEXT_HTML;

    // Error & Feedback Messages
    public static final String ERR_JOB_EMAIL_NOT_FOUND = "Job email not found";
    public static final String ERR_EMPLOYMENT_TYPE_REQUIRED = "employmentType is required";
    public static final String ERR_INVALID_EMPLOYMENT_TYPE_PREFIX = "Invalid employmentType: ";
    public static final String ERR_FAILED_TO_SEND_EMAIL_PREFIX = "Failed to send email: ";
}
