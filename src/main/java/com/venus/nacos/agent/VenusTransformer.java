package com.venus.nacos.agent;

import javassist.*;

import java.io.ByteArrayInputStream;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class VenusTransformer implements ClassFileTransformer {

    @Override
    public byte[] transform(
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer) {
        try {
            String AddressServerMemberLookup = "com/alibaba/nacos/core/cluster/lookup/AddressServerMemberLookup";

            if (AddressServerMemberLookup.equals(className)) {
                return agentAddressServerMemberLookup(classfileBuffer).toBytecode();
            }

            return classfileBuffer;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private CtClass agentAddressServerMemberLookup(byte[] classfileBuffer) throws Exception {
        ClassPool classPool = ClassPool.getDefault();
        //取得当前线程的加载环境,避免代码找不到某些nacos启动环境的spring类等等
        classPool.appendClassPath(new LoaderClassPath(Thread.currentThread().getContextClassLoader()));

        CtClass lookupCtClass = classPool.makeClass(new ByteArrayInputStream(classfileBuffer));
        //扩展的spring properties配置协议前缀
        CtField address_http_protocol =
                new CtField(classPool.get("java.lang.String"), "ADDRESS_HTTP_PROTOCOL",
                        lookupCtClass);
        address_http_protocol.setModifiers(Modifier.PUBLIC);
        lookupCtClass.addField(address_http_protocol, CtField.Initializer.constant("address.http.protocol"));

        //code只要用于判定域名服务器返回码
        CtField http_200 =
                new CtField(classPool.get("java.lang.String"), "HTTP_200",
                        lookupCtClass);
        http_200.setModifiers(Modifier.PUBLIC);
        lookupCtClass.addField(http_200, CtField.Initializer.constant("200"));

        updateInitAddressSysMethod(lookupCtClass);
        //替换为Connection远程连接请求,nacos自身的rest请求修改过多类.
        addDoGetMethod(lookupCtClass);
        updateSyncFromAddressUrlMethod(lookupCtClass);
        return lookupCtClass;
    }


    private void updateInitAddressSysMethod(CtClass lookupCtClass) throws NotFoundException, CannotCompileException {
        CtMethod initAddressSysMethod = lookupCtClass.getDeclaredMethod("initAddressSys");
        initAddressSysMethod.insertAfter("addressServerUrl =  com.alibaba.nacos.sys.env.EnvUtil.getProperty(ADDRESS_HTTP_PROTOCOL) + domainName + \":\" + addressPort + addressUrl;");
        initAddressSysMethod.insertAfter("envIdUrl =  com.alibaba.nacos.sys.env.EnvUtil.getProperty(ADDRESS_HTTP_PROTOCOL) + domainName + \":\" + addressPort + \"/env\";");
        initAddressSysMethod.insertAfter("com.alibaba.nacos.core.utils.Loggers.CORE.info(\"venus agent ServerListService address-server port:\" + addressPort);");
        initAddressSysMethod.insertAfter("com.alibaba.nacos.core.utils.Loggers.CORE.info(\"venus agent ADDRESS_SERVER_URL:\" + addressServerUrl);");
    }

    public void addDoGetMethod(CtClass lookupCtClass) throws Exception {
        CtMethod ctMethod = CtNewMethod.make("public java.util.Map doGet(java.lang.String httpurl) {\n" +
                "        java.util.Map map = new java.util.HashMap();\n" +
                "        java.net.HttpURLConnection connection = null;\n" +
                "        java.io.InputStream is = null;\n" +
                "        java.io.BufferedReader br = null;\n" +
                "        try {\n" +
                "            java.net.URL url = new java.net.URL(httpurl);\n" +
                "            connection = (java.net.HttpURLConnection) url.openConnection();\n" +
                "            if(httpurl.contains(\"https://\")){\n" +
                "                javax.net.ssl.HttpsURLConnection httpsConnection = (javax.net.ssl.HttpsURLConnection)connection;\n" +
                "                com.venus.nacos.extend.VenusSSLSocketFactory.trustAllHosts(httpsConnection);\n" +
                "                httpsConnection.setHostnameVerifier(new com.venus.nacos.extend.VenusHostnameVerifier());\n" +
                "            }\n" +
                "            connection.setRequestMethod(\"GET\");\n" +
                "            connection.setConnectTimeout(15000);\n" +
                "            connection.setReadTimeout(60000);\n" +
                "            connection.connect();\n" +
                "            if (connection.getResponseCode() == java.lang.Integer.parseInt(HTTP_200)) {\n" +
                "                is = connection.getInputStream();\n" +
                "                br = new java.io.BufferedReader(new java.io.InputStreamReader(is, \"UTF-8\"));\n" +
                "                java.lang.StringBuffer sbf = new java.lang.StringBuffer();\n" +
                "                String temp = null;\n" +
                "                while ((temp = br.readLine()) != null) {\n" +
                "                    sbf.append(temp);\n" +
                "                }\n" +
                "                map.put(\"result\", sbf.toString());\n" +
                "            }\n" +
                "            map.put(\"code\", java.lang.Integer.valueOf(connection.getResponseCode()));\n" +
                "            return map;\n" +
                "        } catch (java.net.MalformedURLException e) {\n" +
                "            e.printStackTrace();\n" +
                "        } catch (java.io.IOException e) {\n" +
                "            e.printStackTrace();\n" +
                "        } finally {\n" +
                "            if (null != br) {\n" +
                "                try {\n" +
                "                    br.close();\n" +
                "                } catch (java.io.IOException e) {\n" +
                "                    e.printStackTrace();\n" +
                "                }\n" +
                "            }\n" +
                "            if (null != is) {\n" +
                "                try {\n" +
                "                    is.close();\n" +
                "                } catch (java.io.IOException e) {\n" +
                "                    e.printStackTrace();\n" +
                "                }\n" +
                "            }\n" +
                "            if(connection!=null){\n" +
                "                connection.disconnect();\n" +
                "            }\n" +
                "        }\n" +
                "        return map;\n" +
                "    }", lookupCtClass);
        ctMethod.setModifiers(Modifier.PUBLIC);
        lookupCtClass.addMethod(ctMethod);
    }

    public void updateSyncFromAddressUrlMethod(CtClass lookupCtClass) throws Exception {
        CtMethod ctMethod = lookupCtClass.getDeclaredMethod("syncFromAddressUrl");
        ctMethod.setBody("{\n" +
                "        java.util.Map result = doGet(addressServerUrl);\n" +
                "com.alibaba.nacos.core.utils.Loggers.CORE.info(\"venus agent addressServerUrl:{},result:{}\" ,addressServerUrl,result);\n" +
                "        if (result.get(\"code\")!=null&&HTTP_200.equals(result.get(\"code\").toString())) {\n" +
                "            isAddressServerHealth = true;\n" +
                "            java.io.Reader reader = new java.io.StringReader(result.getOrDefault(\"result\",\"\").toString());\n" +
                "            try {\n" +
                "                java.util.List clusters =  com.alibaba.nacos.sys.env.EnvUtil.analyzeClusterConf(reader);\n"+
                "com.alibaba.nacos.core.utils.Loggers.CORE.info(\"venus agent clusters:{}\" ,clusters);\n" +
                "java.util.Collection members = com.alibaba.nacos.core.cluster.MemberUtil.readServerConf(clusters);\n"+
                "com.alibaba.nacos.core.utils.Loggers.CORE.info(\"venus agent members:{}\" ,members);\n" +
                "                afterLookup(members);\n" +
                "            } catch (java.lang.Throwable e) {\n" +
                "                com.alibaba.nacos.core.utils.Loggers.CLUSTER.error(\"[serverlist] exception for analyzeClusterConf, error : {}\",\n" +
                "                        com.alibaba.nacos.common.utils.ExceptionUtil.getAllExceptionMsg(e));\n" +
                "            }\n" +
                "            addressServerFailCount = 0;\n" +
                "        } else {\n" +
                "            addressServerFailCount++;\n" +
                "            if (addressServerFailCount >= maxFailCount) {\n" +
                "                isAddressServerHealth = false;\n" +
                "            }\n" +
                "            com.alibaba.nacos.core.utils.Loggers.CLUSTER.error(\"[serverlist] failed to get serverlist, error code {}\", result.get(\"code\"));\n" +
                "        }\n" +
                "    }");
    }
}
