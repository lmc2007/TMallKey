package android.app;

import java.util.ArrayList;
import java.util.List;

public class ActivityManager {

    public static class MemoryInfo {
        public long availMem = 2L * 1024 * 1024 * 1024;
        public long totalMem = 8L * 1024 * 1024 * 1024;
        public long threshold = 512L * 1024 * 1024;
        public boolean lowMemory = false;
    }

    public static class RunningAppProcessInfo {
        public String processName;
        public int pid;
        public int uid;
        public int importance;
    }

    public void getMemoryInfo(MemoryInfo outInfo) {
    }

    public List<RunningAppProcessInfo> getRunningAppProcesses() {
        List<RunningAppProcessInfo> list = new ArrayList<>();
        RunningAppProcessInfo p = new RunningAppProcessInfo();
        p.processName = "com.tmall.campus";
        p.pid = android.os.Process.myPid();
        p.uid = android.os.Process.myUid();
        list.add(p);
        return list;
    }

    public int getMemoryClass() {
        return 256;
    }

    public boolean isLowRamDevice() {
        return false;
    }
}
