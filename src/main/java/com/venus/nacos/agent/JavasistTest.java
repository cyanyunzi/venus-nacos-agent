package com.venus.nacos.agent;

import javassist.*;

import java.lang.reflect.Method;

public class JavasistTest {
    public static void main(String[] args) throws Exception {
        ClassPool classPool = ClassPool.getDefault();
        CtClass ctClass = classPool.get("com.venus.nacos.agent.Linwu");

        CtMethod ctMethod = ctClass.getDeclaredMethod("add");
        ctMethod.insertAfter("$_.add(\"2\");");
        Class<?> aClass = ctClass.toClass();
        Object o = aClass.newInstance();
        Method add = aClass.getMethod("add");
        Object invoke = add.invoke(o);
        System.out.println(invoke);




//        ctMethod.

//        CtClass[] paramTypes = {classPool.get(Void.class.getName())};
//        CtMethod insert = ctClass.getDeclaredMethod("insert",paramTypes);
//
//        System.out.println(1);

//        createNewClass();
    }


    private static Class createNewClass() throws Exception {
        ClassPool classPool = ClassPool.getDefault();
        CtClass ctClass = classPool.get("com.venus.nacos.agent.Linwu");
        CtMethod insert = ctClass.getDeclaredMethod("insert");
        System.out.println(1);
        return ctClass.toClass();
    }
}
