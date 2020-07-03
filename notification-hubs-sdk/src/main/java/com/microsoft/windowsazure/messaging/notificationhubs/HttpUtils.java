/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License.
 */

package com.microsoft.windowsazure.messaging.notificationhubs;

import android.content.Context;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.concurrent.RejectedExecutionException;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;

/**
 * HTTP utilities.
 */
class HttpUtils {

    /**
     * Thread stats tag for Notification Hubs HTTP calls.
     */
    public static final int THREAD_STATS_TAG = 0xD83DDC19;

    /**
     * Read buffer size.
     */
    public static final int READ_BUFFER_SIZE = 1024;

    /**
     * Write buffer size.
     */
    public static final int WRITE_BUFFER_SIZE = 1024;

    /**
     * HTTP connection timeout.
     */
    public static final int CONNECT_TIMEOUT = 10000;

    /**
     * HTTP read timeout.
     */
    public static final int READ_TIMEOUT = 10000;

    /**
     * Types of exception that can be retried, no matter what the details are. Sub-classes are included.
     */
    private static final Class[] RECOVERABLE_EXCEPTIONS = {
            EOFException.class,
            InterruptedIOException.class,
            SocketException.class,
            UnknownHostException.class,
            RejectedExecutionException.class
    };
    /**
     * Some transient exceptions can only be detected by interpreting the message...
     */
    private static final Pattern CONNECTION_ISSUE_PATTERN = Pattern.compile("connection (time|reset|abort)|failure in ssl library, usually a protocol error|anchor for certification path not found");

    @VisibleForTesting
    HttpUtils() {
    }

    /**
     * Check whether an exception/error describes a recoverable error or not.
     *
     * @param t exception or error.
     * @return true if the exception/error should be retried, false otherwise.
     */
    public static boolean isRecoverableError(Throwable t) {

        /* Check HTTP exception details. */
        if (t instanceof HttpException) {
            HttpException exception = (HttpException) t;
            int code = exception.getHttpResponse().getStatusCode();
            return code == 503 || code == 408 || code == 429 || code == 403;
        }

        /* Check for a generic exception to retry. */
        for (Class<?> type : RECOVERABLE_EXCEPTIONS) {
            if (type.isAssignableFrom(t.getClass())) {
                return true;
            }
        }

        /* Check the cause. */
        Throwable cause = t.getCause();
        if (cause != null) {
            for (Class<?> type : RECOVERABLE_EXCEPTIONS) {
                if (type.isAssignableFrom(cause.getClass())) {
                    return true;
                }
            }
        }

        /* Check corner cases. */
        if (t instanceof SSLException) {
            String message = t.getMessage();

            //noinspection RedundantIfStatement simplifying would break adding a new block of code later.
            if (message != null && CONNECTION_ISSUE_PATTERN.matcher(message.toLowerCase(Locale.US)).find()) {
                return true;
            }
        }
        return false;
    }

    public static HttpClient createHttpClient(@NonNull Context context) {
        return createHttpClient(context, true);
    }

    public static HttpClient createHttpClient(@NonNull Context context, boolean compressionEnabled) {

        /* Retryer should be applied last to avoid retries in offline. */
        return new HttpClientRetryer(createHttpClientWithoutRetryer(context, compressionEnabled));
    }

    public static HttpClient createHttpClientWithoutRetryer(@NonNull Context context, boolean compressionEnabled) {
        HttpClient httpClient = new DefaultHttpClient(compressionEnabled);
        NetworkStateHelper networkStateHelper = NetworkStateHelper.getSharedInstance(context);
        httpClient = new HttpClientNetworkStateHandler(httpClient, networkStateHelper);
        return httpClient;
    }

    /**
     * Create HTTPS connection.
     *
     * @param url a URL.
     * @return instance of {@link HttpsURLConnection}.
     * @throws IOException if connection fails.
     */
    @NonNull
    public static HttpsURLConnection createHttpsConnection(@NonNull URL url) throws IOException {
        if (!"https".equals(url.getProtocol())) {
            throw new IOException("Notification Hubs only supports HTTPS connections.");
        }
        URLConnection urlConnection = url.openConnection();
        HttpsURLConnection httpsURLConnection;
        if (urlConnection instanceof HttpsURLConnection) {
            httpsURLConnection = (HttpsURLConnection) urlConnection;
        } else {
            throw new IOException("Notification Hubs only supports only HTTPS connections.");
        }

        /*
         * Make sure we use TLS 1.2 when the device supports it but not enabled by default.
         * Don't hardcode TLS version when enabled by default to avoid unnecessary wrapping and
         * to support future versions of TLS such as say 1.3 without having to patch this code.
         *
         * TLS 1.2 was enabled by default only on Android 5.0:
         * https://developer.android.com/about/versions/android-5.0-changes#ssl
         * https://developer.android.com/reference/javax/net/ssl/SSLSocket#default-configuration-for-different-android-versions
         *
         * There is a problem that TLS 1.2 is still disabled by default on some Samsung devices
         * with API 21, so apply the rule to this API level as well.
         * See https://github.com/square/okhttp/issues/2372#issuecomment-244807676
         */

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
            httpsURLConnection.setSSLSocketFactory(new TLS1_2SocketFactory());
        }

        /* Configure connection timeouts. */
        httpsURLConnection.setConnectTimeout(CONNECT_TIMEOUT);
        httpsURLConnection.setReadTimeout(READ_TIMEOUT);
        return httpsURLConnection;
    }
}