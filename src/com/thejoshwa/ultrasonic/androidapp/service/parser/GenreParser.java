/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2010 (C) Sindre Mehus
 */
package com.thejoshwa.ultrasonic.androidapp.service.parser;

import android.content.Context;
import android.util.Log;

import com.thejoshwa.ultrasonic.androidapp.R;
import com.thejoshwa.ultrasonic.androidapp.domain.Genre;
import com.thejoshwa.ultrasonic.androidapp.util.ProgressListener;
import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Joshua Bahnsen
 */
public class GenreParser extends AbstractParser {

	private static final String TAG = GenreParser.class.getSimpleName();
	
    public GenreParser(Context context) {
        super(context);
    }

    public List<Genre> parse(Reader reader, ProgressListener progressListener) throws Exception {
        updateProgress(progressListener, R.string.parser_reading);
        
        List<Genre> result = new ArrayList<Genre>();
        StringReader sr = null;
        
        try {
        	BufferedReader br = new BufferedReader(reader);
        	String xml = null;
        	String line;
        
        	while ((line = br.readLine()) != null) {
        		if (xml == null) {
        			xml = line;
        		} else {
        			xml += line;
        		}
        	}
        	br.close();

            // Replace possible unescaped XML characters
            // No replacements for <> at this time
            if (xml != null) {
                // Replace double escaped ampersand (&amp;apos;)
                xml = xml.replaceAll("(?:&amp;)(amp;|lt;|gt;|#37;|apos;)", "&$1");

                // Replace unescaped ampersand
                xml = xml.replaceAll("&(?!amp;|lt;|gt;|#37;|apos;)", "&amp;");

                // Replace unescaped percent symbol
                xml = xml.replaceAll("%", "&#37;");

                // Replace unescaped apostrophe
                xml = xml.replaceAll("'", "&apos;");
            }

            sr = new StringReader(xml);
        } catch (IOException ioe) {
        	Log.e(TAG, "Error parsing Genre XML", ioe);
        }

        if (sr == null) {
        	Log.w(TAG, "Unable to parse Genre XML, returning empty list");
        	return result;
        }
        
        init(sr);

        Genre genre = null;
        
        int eventType;
        do {
            eventType = nextParseEvent();
            if (eventType == XmlPullParser.START_TAG) {
                String name = getElementName();
                if ("genre".equals(name)) {
                    genre = new Genre();
                } else if ("error".equals(name)) {
                    handleError();
                } else {
                	genre = null;
                }
            } else if (eventType == XmlPullParser.TEXT) {
                if (genre != null) {
                    String value = getText();

                    genre.setName(value);
                    genre.setIndex(value.substring(0, 1));
                    result.add(genre);
                    genre = null;
                }
            }
        } while (eventType != XmlPullParser.END_DOCUMENT);

        validate();
        updateProgress(progressListener, R.string.parser_reading_done);
        
        return result;
    }
}
