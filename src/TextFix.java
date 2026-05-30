import com.nttdocomo.ui.Font;
import com.nttdocomo.ui.Graphics;

/**
 * Bytecode target for selected original-game text layout corrections.
 * The original class call to Graphics.drawString(String,int,int) is patched
 * to this static method.  It preserves normal drawing except for known
 * coordinate literals that need to be recomputed after bitmap font rendering.
 */
public final class TextFix {
    private TextFix() {}

    public static void draw(Graphics g, String s, int x, int y) {
        if (g == null) return;
        if (s != null && "PUSH START BUTTON".equals(s)) {
            int w = Font.getDefaultFont().stringWidth(s);
            x = (240 - w) >> 1;
        }
        g.drawString(s, x, y);
    }
}
