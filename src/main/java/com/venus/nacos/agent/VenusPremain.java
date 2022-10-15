package com.venus.nacos.agent;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.jar.JarFile;

public class VenusPremain {
  public static void premain(String agentArgs, Instrumentation inst) {
    if(!"cdn".equals(agentArgs)){
      System.out.println("cdn return");
      return;
    }

    System.out.println("linwu:agentArgs:"+agentArgs);

    String path = "/Users/zhanglei/code/company/xingchen/venus-nacos-extend/venus-nacos-extend/target/venus-nacos-extend-0.0.1.jar";
    try {
      JarFile jarFile = new JarFile(path);
      inst.appendToBootstrapClassLoaderSearch(jarFile);
      inst.addTransformer(new AddressServerMemberLookupTransformer(), true);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
