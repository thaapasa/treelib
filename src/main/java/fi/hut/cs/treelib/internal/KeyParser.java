package fi.hut.cs.treelib.internal;

import java.lang.reflect.Array;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Key;

public class KeyParser {

    private static final Logger log = Logger.getLogger(KeyParser.class);

    private static final Pattern PAT_SPLITTER = Pattern.compile("[ :\t,<>\\(\\)\\{\\}<>]+");
    private static final Pattern PAT_TRIMMER = Pattern
        .compile("^[ ,<>\\(\\){}]*([-0-9.].*[0-9.])[ ,<>\\(\\)]*");

    /**
     * Parses (numeric) keys from the given string.
     * 
     * @param keyProto the key prototype, used to parse individual keys
     * @param expectedAmount the expected amount of keys in the input
     * @param src the source string to parse
     * @return the parsed keys; or null, if parsing failed for any key or if
     * there was too few keys
     */
    public static String[] parseKeys(int expectedAmount, String src) {
        src = PAT_TRIMMER.matcher(src).replaceFirst("$1");
        String[] parts = PAT_SPLITTER.split(src);

        if (parts.length < expectedAmount)
            return null;

        return parts;
    }

    /**
     * Parses (numeric) keys from the given string.
     * 
     * @param proto the key prototype, used to parse individual keys
     * @param expectedAmount the expected amount of keys in the input
     * @param src the source string to parse
     * @return the parsed keys; or null, if parsing failed for any key or if
     * there was too few keys
     */
    @SuppressWarnings("unchecked")
    public static <K extends Key<K>> K[] parseKeys(K proto, int expectedAmount, String src) {
        String[] parts = parseKeys(expectedAmount, src);
        if (parts == null)
            return null;

        K[] keys = (K[]) Array.newInstance(proto.getClass(), expectedAmount);
        for (int i = 0; i < expectedAmount; i++) {

            if (!proto.isValid(parts[i])) {
                log.warn("Cannot parse value " + parts[i] + " of " + src);
                return null;
            }

            K cur = proto.parse(parts[i]);
            if (cur == null)
                return null;
            keys[i] = cur;
        }

        return keys;
    }
}
