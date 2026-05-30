package com.nttdocomo.ui;

public class Palette {
    private final int[] entries;

    public Palette(int count) {
        if (count < 0) count = 0;
        entries = new int[count];
    }

    public Palette(int[] colors) {
        int i;
        entries = new int[colors == null ? 0 : colors.length];
        for (i = 0; i < entries.length; i++) {
            entries[i] = colors[i];
        }
    }

    public void setEntry(int index, int color) {
        if (index >= 0 && index < entries.length) {
            entries[index] = color;
        }
    }

    public int getEntry(int index) {
        if (index < 0 || index >= entries.length) {
            return 0;
        }
        return entries[index];
    }

    public int getEntryCount() {
        return entries.length;
    }

    int[] copyEntries() {
        int[] copy = new int[entries.length];
        System.arraycopy(entries, 0, copy, 0, entries.length);
        return copy;
    }
}
