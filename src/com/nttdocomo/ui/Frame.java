package com.nttdocomo.ui;

public abstract class Frame extends javax.microedition.lcdui.Canvas {
    public static final int SOFT_KEY_1 = 0;
    public static final int SOFT_KEY_2 = 1;

    private final String[] softLabels = new String[2];
    private int backgroundColor = Graphics.getColorOfName(Graphics.BLACK);
    private boolean softLabelVisible = true;

    public void setBackground(int c) {
        backgroundColor = c;
    }

    public int getBackground() {
        return backgroundColor;
    }

    public void setSoftLabel(int index, String label) {
        if (index >= 0 && index < softLabels.length) {
            softLabels[index] = label;
            repaint();
        }
    }

    public String getSoftLabel(int index) {
        if (index < 0 || index >= softLabels.length) {
            return null;
        }
        return softLabels[index];
    }

    public void setSoftLabelVisible(boolean visible) {
        softLabelVisible = visible;
        repaint();
    }

    public boolean isSoftLabelVisible() {
        return softLabelVisible;
    }
}
