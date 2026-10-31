package com.tmall;

/** 路线② 的入口：SG Java 层跑宿主 JVM，native 走 unidbg。 */
public class RunJava {

    public static void main(String[] args) throws Exception {
        boolean verbose = false;
        for (String a : args) {
            if ("--verbose".equals(a)) {
                verbose = true;
            }
        }
        SGRunner runner = new SGRunner(verbose);
        try {
            runner.run();
        } finally {
            runner.destroy();
        }
        System.out.println("[RUN] done");
    }
}
