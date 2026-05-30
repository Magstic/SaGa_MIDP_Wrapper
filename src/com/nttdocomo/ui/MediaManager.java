package com.nttdocomo.ui;

public class MediaManager {
    public static MediaSound getSound(String uri) {
        String path = normalizeResourcePath(uri);
        if (endsWithIgnoreCase(path, ".mid")) {
            return new SoundRes(-1, "/" + path, "audio/midi", false, 0);
        }
        if (endsWithIgnoreCase(path, ".wav")) {
            return new SoundRes(-1, "/" + path, "audio/x-wav", false, 0);
        }
        return new BasicMediaSound(null, path);
    }

    public static MediaSound getSound(byte[] data) {
        SoundRes converted;

        if (data == null) {
            return new BasicMediaSound(null, "<null>");
        }

        converted = SoundMap.find(data);
        if (converted != null) {
            return converted;
        }

        return new BasicMediaSound(data, "<unmapped byte-array sound>");
    }

    public static MediaImage getImage(String uri) {
        return new BasicMediaImage(null, normalizeResourcePath(uri));
    }

    public static MediaImage getImage(byte[] data) {
        return new BasicMediaImage(data, null);
    }

    private static final class BasicMediaSound implements MediaSound {
        private final byte[] data;
        private final String resourcePath;

        BasicMediaSound(byte[] bytes, String path) {
            data = bytes;
            resourcePath = path;
        }

        public void use() {
            /* Unsupported byte-array or URI sound.  The SaGa build should route
             * all valid sounds through SoundMap; keep this fallback silent on phones. */
        }
    }

    private static final class BasicMediaImage implements MediaImage {
        private final byte[] data;
        private final String resourcePath;
        private Image dojaImage;

        BasicMediaImage(byte[] bytes, String path) {
            data = bytes;
            resourcePath = path;
        }

        public void use() {
            if (dojaImage != null) {
                return;
            }
            try {
                if (data != null) {
                    if (isGif(data)) {
                        dojaImage = PalettedImage.createPalettedImage(data);
                    } else {
                        dojaImage = new Image(javax.microedition.lcdui.Image.createImage(data, 0, data.length));
                    }
                    return;
                }
                loadResourceImage();
            } catch (Exception ignored) {
            }
        }

        private void loadResourceImage() throws java.io.IOException {
            String name = resourcePath == null ? "" : resourcePath;
            int i;
            String[] paths;
            if (name.startsWith("/")) {
                name = name.substring(1);
            }
            paths = new String[] { "/" + name, "/res/" + name };
            for (i = 0; i < paths.length; i++) {
                try {
                    javax.microedition.lcdui.Image midpImg = javax.microedition.lcdui.Image.createImage(paths[i]);
                    dojaImage = new Image(midpImg);
                    return;
                } catch (Exception e) {
                }
            }
            throw new java.io.IOException("image resource not found");
        }

        public Image getImage() {
            use();
            return dojaImage;
        }

        private boolean isGif(byte[] bytes) {
            return bytes != null && bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F';
        }
    }

    private static boolean endsWithIgnoreCase(String value, String suffix) {
        return value != null && value.toLowerCase().endsWith(suffix);
    }

    private static String normalizeResourcePath(String uri) {
        String path = uri == null ? "" : uri;
        if (path.startsWith("resource:///")) {
            path = path.substring("resource:///".length());
        }
        return path;
    }
}
