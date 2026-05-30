package com.nttdocomo.util;

import java.io.InputStream;

/* Compatibility stub kept only because the obfuscated original class still has
 * verifier-visible references to JarInflater.  Normal archive loading is
 * bytecode-patched to SpData and uses build-time split resources under /e/.
 */
public class JarInflater {
    public JarInflater(byte[] bytes) throws JarFormatException {
        if (bytes == null) {
            throw new JarFormatException("null jar bytes");
        }
    }

    public InputStream getInputStream(String name) throws JarFormatException {
        return null;
    }

    public void close() {
    }
}
