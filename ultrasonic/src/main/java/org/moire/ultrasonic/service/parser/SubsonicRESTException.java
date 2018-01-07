package org.moire.ultrasonic.service.parser;

import org.moire.ultrasonic.api.subsonic.SubsonicError;

/**
 * Exception returned by API with given {@code code}.
 *
 * @author Sindre Mehus
 * @version $Id$
 */
public class SubsonicRESTException extends Exception {
    private final SubsonicError error;

    public SubsonicRESTException(final SubsonicError error) {
        super("Api error: " + error.name());
        this.error = error;
    }

    public int getCode()
    {
        return error.getCode();
    }

    public SubsonicError getError() {
        return error;
    }
}
