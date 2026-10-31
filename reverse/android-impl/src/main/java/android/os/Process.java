package android.os;

public class Process {

    public static final int SYSTEM_UID = 1000;

    public static int myPid() {
        return (int) ProcessHandle.current().pid();
    }

    public static int myTid() {
        return myPid();
    }

    public static int myUid() {
        return 10123;
    }

    public static int getUidForPid(int pid) {
        return 10123;
    }

    public static String getProcessName() {
        return "com.tmall.campus";
    }
}
