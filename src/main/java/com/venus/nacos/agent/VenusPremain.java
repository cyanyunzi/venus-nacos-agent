package com.venus.nacos.agent;

import java.lang.instrument.Instrumentation;
import java.util.jar.JarFile;

public class VenusPremain {
    private final static String EXTEND_JAR_DIR = "/venus/";
    private final static String BIN = System.getProperty("user.dir");
    private final static String EXTEND_JAR_NAME = "venus-nacos-extend-0.0.1.jar";
    private final static String EXTEND_JAR_PATH = BIN + EXTEND_JAR_DIR + EXTEND_JAR_NAME;
//    private final static String EXTEND_JAR_PATH = "/Users/zhanglei/code/company/xingchen/venus-nacos-extend/venus-nacos-extend/target/venus-nacos-extend-0.0.1.jar";


    public static void premain(String agentArgs, Instrumentation inst) {
        try {
            Thread.sleep(10000);
            JarFile jarFile = new JarFile(agentArgs);
            inst.appendToSystemClassLoaderSearch(jarFile);
            inst.addTransformer(new VenusTransformer(), true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
