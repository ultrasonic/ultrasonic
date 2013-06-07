package com.thejoshwa.ultrasonic.androidapp.service.parser;

import android.content.Context;
import com.thejoshwa.ultrasonic.androidapp.R;
import com.thejoshwa.ultrasonic.androidapp.domain.Bookmark;
import com.thejoshwa.ultrasonic.androidapp.util.ProgressListener;
import org.xmlpull.v1.XmlPullParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Joshua Bahnsen
 */
public class BookmarkParser extends MusicDirectoryEntryParser {

    public BookmarkParser(Context context) {
        super(context);
    }

    public List<Bookmark> parse(Reader reader, ProgressListener progressListener) throws Exception {

        updateProgress(progressListener, R.string.parser_reading);
        init(reader);

        List<Bookmark> dir = new ArrayList<Bookmark>();
        Bookmark bookmark = null;
        int eventType;
        
        do {
            eventType = nextParseEvent();
            
            if (eventType == XmlPullParser.START_TAG) {
                String name = getElementName();
                
                if ("bookmark".equals(name)) {
                	bookmark = new Bookmark();
                	bookmark.setChanged(get("changed"));
                	bookmark.setCreated(get("created"));
                	bookmark.setComment(get("comment"));
                	bookmark.setPosition(getInteger("position"));
                	bookmark.setUsername(get("username"));
                } else if ("entry".equals(name)) {
                    if (bookmark != null) {
                        bookmark.setEntry(parseEntry(null, false, bookmark.getPosition()));
                        dir.add(bookmark);
                    }
                } else if ("error".equals(name)) {
                    handleError();
                }
            }
        } while (eventType != XmlPullParser.END_DOCUMENT);

        validate();
        updateProgress(progressListener, R.string.parser_reading_done);

        return dir;
    }
}