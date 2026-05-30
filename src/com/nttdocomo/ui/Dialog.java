package com.nttdocomo.ui;

public class Dialog {
    public static final int DIALOG_INFO = 0;
    public static final int DIALOG_WARNING = 1;
    public static final int DIALOG_ERROR = 2;

    private final int type;
    private final String title;
    private String text;

    public Dialog(int type, String title) {
        this.type = type;
        this.title = title;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int show() {
        /* SaGa does not require modal dialog UI.  Keep the DoJa-compatible
         * return value without using console output on phones. */
        return 0;
    }
}
