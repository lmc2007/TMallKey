package android.os;

public class Message {

    public int what;
    public int arg1;
    public int arg2;
    public Object obj;
    public long when;

    public static Message obtain() {
        return new Message();
    }

    public static Message obtain(Handler handler) {
        return new Message();
    }

    public void sendToTarget() {
    }

    public void recycle() {
    }
}
