import javax.microedition.midlet.MIDlet;
import com.nttdocomo.ui.IApplication;

public final class Boot implements Runnable {
    private static final String APP_CLASS_NAME = "App";

    private final MIDlet midlet;

    public Boot(MIDlet midlet) {
        this.midlet = midlet;
    }

    public void run() {
        try {
            IApplication.setMidlet(midlet);
            IApplication app = (IApplication) Class.forName(APP_CLASS_NAME).newInstance();
            IApplication.setCurrentApp(app);
            SpData.preflight();
            app.start();
        } catch (Throwable ignored) {
        }
    }
}
