import javax.microedition.midlet.MIDlet;

public class MainMidlet extends MIDlet {

    private boolean started;

    public void startApp() {
        if (started) {
            return;
        }
        started = true;
        new Thread(new Boot(this)).start();
    }

    public void pauseApp() {
    }

    public void destroyApp(boolean unconditional) {
    }
}
