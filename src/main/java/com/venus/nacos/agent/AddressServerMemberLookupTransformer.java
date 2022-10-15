package com.venus.nacos.agent;

import javassist.*;

import java.io.*;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

public class AddressServerMemberLookupTransformer implements ClassFileTransformer {

    @Override
    public byte[] transform(
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer)
            throws IllegalClassFormatException {
        try {
            String reClassName = "com/alibaba/nacos/core/cluster/lookup/AddressServerMemberLookup";
            if (!reClassName.equals(className)) {
                return classfileBuffer;
            }
            ClassPool classPool = ClassPool.getDefault();
            CtClass lookupCtClass = classPool.makeClass(new ByteArrayInputStream(classfileBuffer));
            CtField address_http_protocol =
                    new CtField(classPool.get("java.lang.String"), "ADDRESS_HTTP_PROTOCOL",
                            lookupCtClass);
            address_http_protocol.setModifiers(Modifier.PUBLIC);
            lookupCtClass.addField(address_http_protocol, CtField.Initializer.constant("address.http.protocol"));

            CtField http_200 =
                    new CtField(classPool.get("java.lang.String"), "HTTP_200",
                            lookupCtClass);
            http_200.setModifiers(Modifier.PUBLIC);
            lookupCtClass.addField(http_200, CtField.Initializer.constant("200"));

            updateInitAddressSysMethod(lookupCtClass);
            addDoGetMethod(lookupCtClass);
            updateSyncFromAddressUrlMethod(lookupCtClass);

            return lookupCtClass.toBytecode();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void updateInitAddressSysMethod(CtClass lookupCtClass) throws NotFoundException, CannotCompileException {
        CtMethod initAddressSysMethod = lookupCtClass.getDeclaredMethod("initAddressSys");
        initAddressSysMethod.insertAfter("addressServerUrl =  com.alibaba.nacos.sys.env.EnvUtil.getProperty(ADDRESS_HTTP_PROTOCOL) + domainName + \":\" + addressPort + addressUrl;");
        initAddressSysMethod.insertAfter("envIdUrl =  com.alibaba.nacos.sys.env.EnvUtil.getProperty(ADDRESS_HTTP_PROTOCOL) + domainName + \":\" + addressPort + \"/env\";");
        initAddressSysMethod.insertAfter("com.alibaba.nacos.core.utils.Loggers.CORE.info(\"venus agent ServerListService address-server port:\" + addressPort);");
        initAddressSysMethod.insertAfter("com.alibaba.nacos.core.utils.Loggers.CORE.info(\"venus agent ADDRESS_SERVER_URL:\" + addressServerUrl);");
        initAddressSysMethod.insertAfter("System.out.println(\"venus agent ServerListService address-server port:\" + addressPort);");
        initAddressSysMethod.insertAfter("System.out.println(\"venus agent ADDRESS_SERVER_URL:\" + addressServerUrl);");
    }

    public void addDoGetMethod(CtClass lookupCtClass) throws Exception {
        CtMethod ctMethod = CtMethod.make("public java.util.Map<java.lang.String, java.lang.Object> doGet(java.lang.String httpurl) {\n" +
                "        System.out.println(\"venus.agent.httpurl:\"+httpurl);\n" +
                "        java.util.Map<java.lang.String, java.lang.Object> map = new java.util.HashMap<>();\n" +
                "        java.net.HttpURLConnection connection = null;\n" +
                "        java.io.InputStream is = null;\n" +
                "        java.io.BufferedReader br = null;\n" +
                "        try {\n" +
                "            java.net.URL url = new java.net.URL(httpurl);\n" +
                "            connection = (java.net.HttpURLConnection) url.openConnection();\n" +
                "            if(httpurl.contains(\"https://\")){\n" +
                "                javax.net.ssl.HttpsURLConnection httpsConnection = (javax.net.ssl.HttpsURLConnection)connection;\n" +
                "                com.alibaba.nacos.core.cluster.venus.VenusSSLSocketFactory.trustAllHosts(httpsConnection);\n" +
                "                httpsConnection.setHostnameVerifier(new com.alibaba.nacos.core.cluster.venus.VenusHostnameVerifier());\n" +
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
                "                    sbf.append(\"\\r\\n\");\n" +
                "                }\n" +
                "                map.put(\"result\", sbf.toString());\n" +
                "            }\n" +
                "            map.put(\"code\", connection.getResponseCode());\n" +
                "            System.out.println(\"venus.agent.map:\"+map);\n" +
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
        ctMethod.setBody("private void syncFromAddressUrl(){\n" +
                "        System.out.println(\"venus.agent.syncFromAddressUrl\");\n" +
                "        java.util.Map<java.lang.String, java.lang.Object> result = doGet(addressServerUrl);\n" +
                "        if (result.get(\"code\")!=null&&HTTP_200.equals(result.get(\"code\"))) {\n" +
                "            isAddressServerHealth = true;\n" +
                "            java.io.Reader reader = new java.io.StringReader(result.getOrDefault(\"result\",\"\").toString());\n" +
                "            try {\n" +
                "                afterLookup(com.alibaba.nacos.core.cluster.MemberUtil.readServerConf(com.alibaba.nacos.sys.env.EnvUtil.analyzeClusterConf(reader)));\n" +
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


    public static byte[] getClassBytes(String fileName) {
        File file = new File(fileName);
        try (InputStream is = new FileInputStream(file);
             ByteArrayOutputStream bs = new ByteArrayOutputStream()) {
            long length = file.length();
            byte[] bytes = new byte[(int) length];

            int n;
            while ((n = is.read(bytes)) != -1) {
                bs.write(bytes, 0, n);
            }
            return bytes;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
