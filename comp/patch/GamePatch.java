import java.io.File;
import java.io.IOException;
import doja.tools.classfile.MethodRefPatch;

/** Makai Toushi SaGa left-soft-key sound-control redirects. */
public final class GamePatch {
    private GamePatch() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IOException("Usage: GamePatch <class-dir>");
        File classes = new File(args[0]);
        int next = MethodRefPatch.redirect(classes, "j", "e", "()V",
                "SagaSoundControl", "next");
        int refresh = MethodRefPatch.redirect(classes, "j", "f", "()V",
                "SagaSoundControl", "refresh");
        if (next != 1 || refresh != 1) {
            throw new IOException("SaGa sound-control references changed: j.e=" + next
                    + ", j.f=" + refresh);
        }
        System.out.println("GamePatch: SaGa sound-control references redirected");
    }
}
