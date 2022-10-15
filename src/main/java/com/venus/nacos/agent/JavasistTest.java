package com.venus.nacos.agent;

import javassist.*;

public class JavasistTest {
    public static void main(String[] args) throws Exception {
        ClassPool classPool = ClassPool.getDefault();
        CtClass ctClass = classPool.get("com.venus.nacos.agent.Linwu");

        CtMethod insert = ctClass.getDeclaredMethod("insert");
//        CtMethod insert = ctClass.getDeclaredMethod("insert");
//
//        CtClass[] paramTypes = {classPool.get(Void.class.getName())};
//        CtMethod insert = ctClass.getDeclaredMethod("insert",paramTypes);
//
//        System.out.println(1);

//        createNewClass();
    }

    private static CtClass getMyHostnameVerifier(ClassPool classPool)
            throws NotFoundException, CannotCompileException {
        CtClass ctClass = classPool.makeClass("com.venus.nacos.agent.MyHostnameVerifier");
        CtClass HostnameVerifierClass = classPool.get("javax.net.ssl.HostnameVerifier");
        ctClass.addInterface(HostnameVerifierClass);

        CtMethod ctMethod = CtMethod.make("public boolean verify(){return true;}", ctClass);
        ctClass.addMethod(ctMethod);

        return ctClass;
    }

    private static Class createNewClass() throws Exception {
        ClassPool classPool = ClassPool.getDefault();
        CtClass ctClass = classPool.get("com.venus.nacos.agent.Linwu");
        CtMethod insert = ctClass.getDeclaredMethod("insert");
        System.out.println(1);
        return ctClass.toClass();
    }
}
