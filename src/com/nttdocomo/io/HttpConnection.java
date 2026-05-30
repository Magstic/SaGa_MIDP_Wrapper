package com.nttdocomo.io;

import java.io.IOException;
import java.io.InputStream;

public interface HttpConnection extends javax.microedition.io.ContentConnection {
    String GET = "GET";
    String POST = "POST";

    void setRequestMethod(String method) throws IOException;
    void connect() throws IOException;
    InputStream openInputStream() throws IOException;
    void close() throws IOException;
}
