package com.tmall;

import com.github.unidbg.linux.android.dvm.BaseVM;
import com.github.unidbg.linux.android.dvm.DvmClass;
import com.github.unidbg.linux.android.dvm.DvmClassFactory;

/**
 * 纯 DvmClass 工厂：不用 ProxyClassFactory。
 *
 * unidbg 默认的 ProxyClassFactory 会给每个类挂 ProxyJni，先尝试用宿主 JVM 反射调用真实 Java 类，
 * 找不到才回落到我们的 Jni。反射路径会抛 IllegalArgumentException / 打一堆 warn，
 * 而 SecurityGuard 的 Java 层根本不在宿主 JVM 里 —— 直接关掉更干净。
 */
public class PlainClassFactory implements DvmClassFactory {

    @Override
    public DvmClass createClass(BaseVM vm, String className, DvmClass superClass, DvmClass[] interfaceClasses) {
        return new Plain(vm, className, superClass, interfaceClasses);
    }

    private static final class Plain extends DvmClass {
        Plain(BaseVM vm, String className, DvmClass superClass, DvmClass[] interfaceClasses) {
            super(vm, className, superClass, interfaceClasses);
        }
    }
}
