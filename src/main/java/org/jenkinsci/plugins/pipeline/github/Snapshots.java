package org.jenkinsci.plugins.pipeline.github;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import org.kohsuke.github.GHUser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Date;
import java.util.Locale;

/**
 * Conversions from hub4j values to the plain values the Groovy objects keep: Strings for URLs and enums (in the
 * lower case the REST API returns, as egit exposed them), copies of Dates, logins for users.
 */
final class Snapshots {

    private Snapshots() {
    }

    @CheckForNull
    static String url(@CheckForNull final URL url) {
        return url == null ? null : url.toString();
    }

    @CheckForNull
    static String lower(@CheckForNull final Enum<?> value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }

    @CheckForNull
    static Date copy(@CheckForNull final Date date) {
        return date == null ? null : new Date(date.getTime());
    }

    @CheckForNull
    static String login(@CheckForNull final GHUser user) {
        return user == null ? null : user.getLogin();
    }

    /** Runs a hub4j getter that declares IOException (some lazily fetch the rest of the object). */
    static <T> T unchecked(final IOCall<T> call) {
        try {
            return call.call();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @FunctionalInterface
    interface IOCall<T> {
        T call() throws IOException;
    }
}
