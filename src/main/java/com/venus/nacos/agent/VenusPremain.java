package com.venus.nacos.agent;

import java.lang.instrument.Instrumentation;
import java.util.jar.JarFile;

public class VenusPremain {
    public static void premain(String agentArgs, Instrumentation inst) {
        try {
            JarFile jarFile = new JarFile(agentArgs);
            inst.appendToSystemClassLoaderSearch(jarFile);
            inst.addTransformer(new VenusTransformer(), true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
