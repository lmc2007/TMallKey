package com.tmall;

import com.github.unidbg.Emulator;
import com.github.unidbg.Module;
import com.github.unidbg.arm.backend.Backend;
import com.github.unidbg.arm.backend.CodeHook;
import unicorn.Arm64Const;

/**
 * 指令级探针：在指定偏移上挂 CodeHook，打印寄存器与 LR。
 *
 * 用法：-Dsg.trace=0x39338,0x11F73C
 *      -Dsg.trace.regs=x0-x7       （默认 x0-x7）
 *      -Dsg.trace.max=200          （每个地址最多打印多少次）
 *
 * 说明：unicorn2 与 dynarmic 都实现了 Backend#hook_add_new(CodeHook,…)，单点是 begin..begin+4。
 */
public final class SGTrace {

    /** 需要顺带打印的栈槽偏移（相对 SP），来自 -Dsg.trace.sp=0x50,0x28 */
    private static final long[] SP_OFFSETS = parseSp();

    private static long[] parseSp() {
        String s = System.getProperty("sg.trace.sp", "");
        if (s.isEmpty()) {
            return new long[0];
        }
        String[] p = s.split(",");
        long[] out = new long[p.length];
        for (int i = 0; i < p.length; i++) {
            try {
                out[i] = Long.decode(p[i].trim());
            } catch (Throwable t) {
                out[i] = 0;
            }
        }
        return out;
    }

    private SGTrace() {
    }

    public static void install(Emulator<?> emulator, Module module, String spec) {
        install0(emulator, module, spec);
    }

    /**
     * 同一个 so 可能被加载出多个副本（插件初始化会再 loadLibrary 一次），
     * 执行的是最后一个副本，所以按名字把所有副本都挂上。
     */
    public static void installOnAll(Emulator<?> emulator, String soName, String spec) {
        if (spec == null || spec.isEmpty()) {
            return;
        }
        int n = 0;
        for (Module m : emulator.getMemory().getLoadedModules()) {
            if (soName != null && !soName.equals(m.name)) {
                continue;
            }
            install0(emulator, m, spec);
            n++;
        }
        System.out.println("[TRACE] installOnAll " + soName + " copies=" + n);
    }

    private static void install0(Emulator<?> emulator, Module module, String spec) {
        if (spec == null || spec.isEmpty()) {
            return;
        }
        int max = Integer.getInteger("sg.trace.max", 200);
        String regSpec = System.getProperty("sg.trace.regs", "x0-x7");
        int[] regs = parseRegs(regSpec);
        Backend backend = emulator.getBackend();

        for (String part : spec.split(",")) {
            String s = part.trim();
            if (s.isEmpty()) {
                continue;
            }
            final long off;
            try {
                off = Long.decode(s);
            } catch (NumberFormatException e) {
                System.out.println("[TRACE] bad addr " + s);
                continue;
            }
            final long addr = module.base + off;
            final int[] counter = {0};
            backend.hook_add_new(new CodeHook() {
                @Override
                public void hook(Backend be, long address, int size, Object user) {
                    if (counter[0]++ >= max) {
                        return;
                    }
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("[T] %06x", off));
                    for (int r : regs) {
                        try {
                            long v = be.reg_read(r).longValue();
                            sb.append(String.format(" %s=%x", regName(r), v));
                        } catch (Throwable t) {
                            sb.append(" ").append(regName(r)).append("=?");
                        }
                    }
                    try {
                        long lr = be.reg_read(unicorn.Arm64Const.UC_ARM64_REG_LR).longValue();
                        sb.append(String.format(" lr=%x(%s)", lr, offOf(module, lr)));
                    } catch (Throwable ignored) {
                    }
                    try {
                        long sp = be.reg_read(unicorn.Arm64Const.UC_ARM64_REG_SP).longValue();
                        for (long o : SP_OFFSETS) {
                            byte[] buf = be.mem_read(sp + o, 8);
                            long v = 0;
                            for (int i = 7; i >= 0; i--) {
                                v = (v << 8) | (buf[i] & 0xff);
                            }
                            sb.append(String.format(" [sp+%x]=%x(%s)", o, v, offOf(module, v)));
                        }
                    } catch (Throwable t) {
                        sb.append(" sp=?");
                    }
                    System.out.println(sb);
                    String deref = System.getProperty("sg.trace.deref");
                    if (deref != null && !deref.isEmpty()) {
                        // 形如 x10:32 —— 以 x10 里的指针为起点 dump 32 字节
                        String[] p = deref.split(":");
                        try {
                            int reg = X[Integer.parseInt(p[0].replace("x", "").trim())];
                            int len = p.length > 1 ? Integer.parseInt(p[1].trim()) : 32;
                            long base = be.reg_read(reg).longValue();
                            byte[] buf = be.mem_read(base, len);
                            StringBuilder hex = new StringBuilder("[MEM] " + p[0] + "->");
                            for (int i = 0; i < buf.length; i += 8) {
                                long v = 0;
                                for (int j = 7; j >= 0 && i + j < buf.length; j--) {
                                    v = (v << 8) | (buf[i + j] & 0xff);
                                }
                                hex.append(String.format(" +%x:%x", i, v));
                            }
                            System.out.println(hex);
                        } catch (Throwable t) {
                            System.out.println("[MEM] deref fail " + t);
                        }
                    }
                }

                @Override
                public void detach() {
                }

                @Override
                public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {
                }
            }, addr, addr + 4, null);
            System.out.println(String.format("[TRACE] hook %s -> 0x%x", s, addr));
        }
    }

    private static String offOf(Module module, long lr) {
        long o = lr - module.base;
        if (o < 0 || o > 0x4000000L) {
            return "outside";
        }
        return String.format("%06x", o);
    }

    private static final int[] X = new int[32];

    static {
        int[] base = {
                unicorn.Arm64Const.UC_ARM64_REG_X0,
                unicorn.Arm64Const.UC_ARM64_REG_X1,
                unicorn.Arm64Const.UC_ARM64_REG_X2,
                unicorn.Arm64Const.UC_ARM64_REG_X3,
                unicorn.Arm64Const.UC_ARM64_REG_X4,
                unicorn.Arm64Const.UC_ARM64_REG_X5,
                unicorn.Arm64Const.UC_ARM64_REG_X6,
                unicorn.Arm64Const.UC_ARM64_REG_X7,
                unicorn.Arm64Const.UC_ARM64_REG_X8,
        };
        System.arraycopy(base, 0, X, 0, base.length);
    }

    private static int[] parseRegs(String spec) {
        // 只支持 x0-x8 这种简单写法
        try {
            String[] p = spec.split("-");
            int from = Integer.parseInt(p[0].replace("x", "").trim());
            int to = p.length > 1 ? Integer.parseInt(p[1].replace("x", "").trim()) : from;
            int n = Math.max(0, Math.min(9, to - from + 1));
            int[] out = new int[n];
            for (int i = 0; i < n; i++) {
                out[i] = X[from + i];
            }
            return out;
        } catch (Throwable t) {
            return new int[]{X[0], X[1], X[2]};
        }
    }

    private static String regName(int reg) {
        for (int i = 0; i < X.length; i++) {
            if (X[i] == reg) {
                return "x" + i;
            }
        }
        return "r" + reg;
    }
}
