package com.docklet.platform;

/** Stable error codes for module installation/runtime failures. */
public final class ModuleException extends Exception {

    public static final String PACKAGE_MISSING = "PACKAGE_MISSING";
    public static final String PACKAGE_TOO_LARGE = "PACKAGE_TOO_LARGE";
    public static final String PACKAGE_CORRUPT = "PACKAGE_CORRUPT";
    public static final String UNSAFE_ENTRY = "UNSAFE_ENTRY";
    public static final String MANIFEST_MISSING = "MANIFEST_MISSING";
    public static final String MANIFEST_INVALID = "MANIFEST_INVALID";
    public static final String ENTRY_MISSING = "ENTRY_MISSING";
    public static final String UNSUPPORTED_SHELL = "UNSUPPORTED_SHELL";
    public static final String INVALID_ID = "INVALID_ID";
    public static final String VERSION_CONFLICT = "VERSION_CONFLICT";
    public static final String VERSION_DOWNGRADE = "VERSION_DOWNGRADE";
    public static final String NOT_INSTALLED = "NOT_INSTALLED";
    public static final String NO_PREVIOUS_VERSION = "NO_PREVIOUS_VERSION";
    public static final String INSTALL_FAILED = "INSTALL_FAILED";
    public static final String UNINSTALL_FAILED = "UNINSTALL_FAILED";
    public static final String STORAGE_ERROR = "STORAGE_ERROR";
    public static final String SQL_REJECTED = "SQL_REJECTED";
    public static final String FILES_ERROR = "FILES_ERROR";
    public static final String PATH_REJECTED = "PATH_REJECTED";
    public static final String FILE_NOT_FOUND = "FILE_NOT_FOUND";
    public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";
    public static final String FILE_CONFLICT = "FILE_CONFLICT";
    public static final String REMOTE_CATALOG_ERROR = "REMOTE_CATALOG_ERROR";
    public static final String REMOTE_URL_REJECTED = "REMOTE_URL_REJECTED";
    public static final String REMOTE_HASH_MISMATCH = "REMOTE_HASH_MISMATCH";
    public static final String REMOTE_ID_MISMATCH = "REMOTE_ID_MISMATCH";

    private final String code;

    public ModuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ModuleException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static ModuleException of(String code, String message) {
        return new ModuleException(code, message);
    }
}
