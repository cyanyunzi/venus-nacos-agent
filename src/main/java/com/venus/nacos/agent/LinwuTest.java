package com.venus.nacos.agent;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

import java.lang.reflect.Method;

public class LinwuTest {


    public static void main(String[] args) throws Exception {
        ClassPool classPool = ClassPool.getDefault();
        CtClass ctClass = classPool.get("com.venus.nacos.agent.Linwu");

        CtMethod ctMethod = ctClass.getDeclaredMethod("add");

        ctMethod.setBody("{java.util.List result = new java.util.ArrayList();\n" +
                "        result.add(\"1\");\n" +
                "        result.add(\"2\");\n" +
                "\n" +
                "        for (int i = 0; i < result.size(); i++) {\n" +
                "            java.lang.System.out.println(result.get(i));\n" +
                "        }\n" +
                "        Integer.parseInt(num.toString());\n" +
                "        return result;}");


        Class<?> aClass = ctClass.toClass();
        Object o = aClass.newInstance();
        Method add = aClass.getMethod("add");

        Object invoke = add.invoke(o);
        System.out.println(invoke);
    }





}
