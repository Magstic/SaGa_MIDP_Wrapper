package com.nttdocomo.util;

import java.io.IOException;

public class JarFormatException extends IOException {
    public JarFormatException() {
        super();
    }

    public JarFormatException(String message) {
        super(message);
    }
}
