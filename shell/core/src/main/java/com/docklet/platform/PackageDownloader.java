package com.docklet.platform;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;

/** Downloads remote module packages and verifies the catalog sha256. */
public final class PackageDownloader {

    private PackageDownloader() {
    }

    /**
     * Downloads a package from a user-supplied URL without a catalog record.
     * The caller owns the returned temporary file and must delete it.
     */
    /** 下载进度回调，total 为 -1 表示服务端没给长度。 */
    public interface ProgressListener {
        void onProgress(long received, long total);
    }

    public static File downloadUrl(String urlText, String expectedSha256, File cacheDir)
            throws ModuleException {
        return downloadUrl(urlText, expectedSha256, cacheDir, null);
    }

    public static File downloadUrl(
            String urlText,
            String expectedSha256,
            File cacheDir,
            ProgressListener listener
    ) throws ModuleException {
        return downloadUrl(urlText, expectedSha256, cacheDir, listener, null);
    }

    public static File downloadUrl(
            String urlText,
            String expectedSha256,
            File cacheDir,
            ProgressListener listener,
            java.util.function.BooleanSupplier cancelled
    ) throws ModuleException {
        if (urlText == null || urlText.trim().isEmpty()) {
            throw new ModuleException(ModuleException.REMOTE_URL_REJECTED, "缺少模块包 URL");
        }
        if (urlText.length() > 2048) {
            throw new ModuleException(ModuleException.REMOTE_URL_REJECTED, "URL 过长");
        }
        if (cacheDir == null) {
            throw new ModuleException(ModuleException.REMOTE_CATALOG_ERROR, "下载目录为空");
        }
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw new ModuleException(ModuleException.REMOTE_CATALOG_ERROR, "无法创建下载目录");
        }

        URL url;
        try {
            url = new URL(urlText.trim());
        } catch (Exception error) {
            throw new ModuleException(ModuleException.REMOTE_URL_REJECTED, "URL 不合法");
        }
        if (!"http".equalsIgnoreCase(url.getProtocol())
                && !"https".equalsIgnoreCase(url.getProtocol())) {
            throw new ModuleException(ModuleException.REMOTE_URL_REJECTED, "只支持 http/https");
        }
        if (url.getUserInfo() != null) {
            throw new ModuleException(ModuleException.REMOTE_URL_REJECTED, "URL 不允许包含用户名密码");
        }

        // ModuleManager requires the .pkg extension even for a temporary file.
        File temp = new File(
                cacheDir,
                ".url-" + UUID.randomUUID() + ".pkg"
        );
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(30_000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/octet-stream");
            int status = connection.getResponseCode();
            if ("https".equalsIgnoreCase(url.getProtocol())
                    && !"https".equalsIgnoreCase(connection.getURL().getProtocol())) {
                throw new ModuleException(
                        ModuleException.REMOTE_URL_REJECTED,
                        "HTTPS 被重定向到 HTTP，已拒绝"
                );
            }
            if (status < 200 || status >= 300) {
                throw new ModuleException(
                        ModuleException.REMOTE_CATALOG_ERROR,
                        "下载失败: HTTP " + status
                );
            }
            long declared = connection.getContentLengthLong();
            if (declared > ModuleManager.MAX_PACKAGE_BYTES) {
                throw new ModuleException(
                        ModuleException.PACKAGE_TOO_LARGE,
                        "模块包超过 50 MB"
                );
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total = 0L;
            long expected = declared > 0 ? declared : -1L;
            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (listener != null) listener.onProgress(total, expected);
                    if (cancelled != null && cancelled.getAsBoolean()) {
                        throw new ModuleException(ModuleException.INSTALL_FAILED, "已取消");
                    }
                    if (total > ModuleManager.MAX_PACKAGE_BYTES) {
                        throw new ModuleException(
                                ModuleException.PACKAGE_TOO_LARGE,
                                "模块包超过 50 MB"
                        );
                    }
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
            }
            if (expectedSha256 != null && !expectedSha256.trim().isEmpty()) {
                String actual = toHex(digest.digest());
                if (!actual.equalsIgnoreCase(expectedSha256.trim())) {
                    throw new ModuleException(
                            ModuleException.REMOTE_HASH_MISMATCH,
                            "sha256 校验失败"
                    );
                }
            }
            return temp;
        } catch (ModuleException error) {
            deleteQuietly(temp);
            throw error;
        } catch (Exception error) {
            deleteQuietly(temp);
            throw new ModuleException(
                    ModuleException.REMOTE_CATALOG_ERROR,
                    "下载失败: " + error.getMessage(),
                    error
            );
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void deleteQuietly(File file) {
        try {
            java.nio.file.Files.deleteIfExists(file.toPath());
        } catch (IOException ignored) {
            // Cleanup is best effort.
        }
    }

    public static File download(RemoteModule module, File cacheDir) throws ModuleException {
        if (module == null) {
            throw new ModuleException(ModuleException.REMOTE_CATALOG_ERROR, "远程模块为空");
        }
        if (cacheDir == null) {
            throw new ModuleException(ModuleException.REMOTE_CATALOG_ERROR, "下载目录为空");
        }
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw new ModuleException(ModuleException.REMOTE_CATALOG_ERROR, "无法创建下载目录");
        }

        URL url;
        try {
            url = new URL(module.packageUrl());
        } catch (Exception error) {
            throw new ModuleException(ModuleException.REMOTE_URL_REJECTED, "模块包 URL 不合法");
        }
        if (!"http".equalsIgnoreCase(url.getProtocol())
                && !"https".equalsIgnoreCase(url.getProtocol())) {
            throw new ModuleException(ModuleException.REMOTE_URL_REJECTED, "模块包只支持 http/https");
        }

        File target = new File(cacheDir, module.id() + "-" + module.version() + ".pkg");
        File temp = new File(
                cacheDir,
                "." + module.id() + "-" + UUID.randomUUID() + ".download"
        );
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(30_000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/octet-stream");
            int status = connection.getResponseCode();
            if ("https".equalsIgnoreCase(url.getProtocol())
                    && !"https".equalsIgnoreCase(connection.getURL().getProtocol())) {
                throw new ModuleException(
                        ModuleException.REMOTE_URL_REJECTED,
                        "HTTPS 模块包被重定向到 HTTP，已拒绝"
                );
            }
            if (status < 200 || status >= 300) {
                throw new ModuleException(
                        ModuleException.REMOTE_CATALOG_ERROR,
                        "下载失败: HTTP " + status
                );
            }
            long declared = connection.getContentLengthLong();
            if (declared > ModuleManager.MAX_PACKAGE_BYTES) {
                throw new ModuleException(ModuleException.PACKAGE_TOO_LARGE, "模块包超过 50 MB");
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total = 0L;
            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > ModuleManager.MAX_PACKAGE_BYTES) {
                        throw new ModuleException(ModuleException.PACKAGE_TOO_LARGE, "模块包超过 50 MB");
                    }
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
            }
            if (module.size() > 0L && total != module.size()) {
                throw new ModuleException(
                        ModuleException.REMOTE_CATALOG_ERROR,
                        "模块包大小与 catalog 不一致"
                );
            }
            String actual = toHex(digest.digest());
            if (!actual.equalsIgnoreCase(module.sha256())) {
                throw new ModuleException(
                        ModuleException.REMOTE_HASH_MISMATCH,
                        "sha256 校验失败"
                );
            }
            FileOps.move(temp.toPath(), target.toPath());
            return target;
        } catch (ModuleException error) {
            throw error;
        } catch (Exception error) {
            throw new ModuleException(
                    ModuleException.REMOTE_CATALOG_ERROR,
                    "下载失败: " + error.getMessage(),
                    error
            );
        } finally {
            if (connection != null) connection.disconnect();
            try {
                java.nio.file.Files.deleteIfExists(temp.toPath());
            } catch (IOException ignored) {
                // Cleanup is best effort.
            }
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            out.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return out.toString();
    }
}
