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
            String ExternalDataSourceProperties = "com/alibaba/nacos/config/server/service/datasource/ExternalDataSourceProperties";

            if (AddressServerMemberLookup.equals(className)) {
                return agentAddressServerMemberLookup(classfileBuffer).toBytecode();
            }

            if (ExternalDataSourceProperties.equals(className)) {
                return agentExternalDataSourceProperties(classfileBuffer).toBytecode();
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

    private CtClass agentExternalDataSourceProperties(byte[] classfileBuffer) throws Exception {
        ClassPool classPool = ClassPool.getDefault();
        classPool.appendClassPath(new LoaderClassPath(Thread.currentThread().getContextClassLoader()));
        CtClass externalDataSourcePropertiesCtClass = classPool.makeClass(new ByteArrayInputStream(classfileBuffer));
        CtMethod ctMethod = externalDataSourcePropertiesCtClass.getDeclaredMethod("build");
        //修改方法返回值,$_是返回值,dataSources修改密码为明文
        ctMethod.insertAfter("java.util.Iterator var8 = $_.iterator();\n" +
                "        while(var8.hasNext()) {\n" +
                "            com.zaxxer.hikari.HikariDataSource dataSource = (com.zaxxer.hikari.HikariDataSource)var8.next();\n" +
                "            String encryptPassword = dataSource.getPassword();\n" +
                "            String decryptPassword = com.alibaba.nacos.core.cluster.venus.VenusAesUtil.decrypt(encryptPassword);\n" +
                "            dataSource.setPassword(decryptPassword);\n" +
                "            com.alibaba.nacos.core.utils.Loggers.CORE.info(\"venus.agent:password encryptPassword:{} decryptPassword:{}\",encryptPassword,decryptPassword);\n" +
                "        }");
        return externalDataSourcePropertiesCtClass;
    }
    /*
        java.util.Iterator var8 = dataSources.iterator();
        while(var8.hasNext()) {
            com.zaxxer.hikari.HikariDataSource dataSource = (com.zaxxer.hikari.HikariDataSource)var8.next();
            String encryptPassword = dataSource.getPassword();
            String decryptPassword = com.alibaba.nacos.core.cluster.venus.VenusAesUtil.decrypt(encryptPassword);
            dataSource.setPassword(decryptPassword);
            com.alibaba.nacos.core.utils.Loggers.CORE.info("venus.agent:password encryptPassword:{} decryptPassword:{}",encryptPassword,decryptPassword);
        }
    *
    * */

    /*

    {
        java.util.List dataSources = new java.util.ArrayList();
        org.springframework.boot.context.properties.bind.Binder.get($1).bind("db", org.springframework.boot.context.properties.bind.Bindable.ofInstance(this));
        com.alibaba.nacos.common.utils.Preconditions.checkArgument(java.util.Objects.nonNull(num), "db.num is null");
        com.alibaba.nacos.common.utils.Preconditions.checkArgument(org.apache.commons.collections.CollectionUtils.isNotEmpty(user), "db.user or db.user.[index] is null");
        com.alibaba.nacos.common.utils.Preconditions.checkArgument(org.apache.commons.collections.CollectionUtils.isNotEmpty(password), "db.password or db.password.[index] is null");
        for (int index = 0; index < java.lang.Integer.parseInt(num.toString()); index++) {
            int currentSize = index + 1;
            com.alibaba.nacos.common.utils.Preconditions.checkArgument(url.size() >= currentSize, "db.url.%s is null", new Object[]{index});
            com.alibaba.nacos.config.server.service.datasource.DataSourcePoolProperties poolProperties = com.alibaba.nacos.config.server.service.datasource.DataSourcePoolProperties.build($1);
            poolProperties.setDriverClassName(JDBC_DRIVER_NAME);
            poolProperties.setJdbcUrl(url.get(index).toString().trim());
            poolProperties.setUsername(com.alibaba.nacos.common.utils.CollectionUtils.getOrDefault(user, index, user.get(0).toString()).toString().trim());
            try {
                String encryptPassword = com.alibaba.nacos.common.utils.CollectionUtils.getOrDefault(password, index, password.get(0).toString()).toString().trim();
                String decryptPassword = com.alibaba.nacos.core.cluster.venus.VenusAesUtil.decrypt(encryptPassword);
                com.alibaba.nacos.core.utils.Loggers.CORE.info("venus.agent:password encryptPassword:{} decryptPassword:{}",encryptPassword,decryptPassword);
                poolProperties.setPassword(decryptPassword);
            } catch (Exception e) {
                com.alibaba.nacos.core.utils.Loggers.CORE.error("venus.agent:decrypt.error msg:{} ex:{}",e.getMessage(),e);
            }

            com.zaxxer.hikari.HikariDataSource ds = poolProperties.getDataSource();
            ds.setConnectionTestQuery(TEST_QUERY);
            ds.setIdleTimeout(java.util.concurrent.TimeUnit.MINUTES.toMillis(10L));
            ds.setConnectionTimeout(java.util.concurrent.TimeUnit.SECONDS.toMillis(3L));
            dataSources.add(ds);
            $2.accept(ds);
        }
        com.alibaba.nacos.common.utils.Preconditions.checkArgument(org.apache.commons.collections.CollectionUtils.isNotEmpty(dataSources), "no datasource available");
        return dataSources;
    }



    * */

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



    /*public java.util.Map doGet(java.lang.String httpurl) {
        java.util.Map map = new java.util.HashMap();
        java.net.HttpURLConnection connection = null;
        java.io.InputStream is = null;
        java.io.BufferedReader br = null;
        try {
            java.net.URL url = new java.net.URL(httpurl);
            connection = (java.net.HttpURLConnection) url.openConnection();
            if(httpurl.contains("https://")){
                javax.net.ssl.HttpsURLConnection httpsConnection = (javax.net.ssl.HttpsURLConnection)connection;
                com.alibaba.nacos.core.cluster.venus.VenusSSLSocketFactory.trustAllHosts(httpsConnection);
                httpsConnection.setHostnameVerifier(new com.alibaba.nacos.core.cluster.venus.VenusHostnameVerifier());
            }
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(60000);
            connection.connect();
            if (connection.getResponseCode() == java.lang.Integer.parseInt(HTTP_200)) {
                is = connection.getInputStream();
                br = new java.io.BufferedReader(new java.io.InputStreamReader(is, "UTF-8"));
                java.lang.StringBuffer sbf = new java.lang.StringBuffer();
                String temp = null;
                while ((temp = br.readLine()) != null) {
                    sbf.append(temp);
                    sbf.append("\r\n");
                }
                map.put("result", sbf.toString());
            }
            map.put("code", java.lang.Integer.valueOf(connection.getResponseCode()));
            return map;
        } catch (java.net.MalformedURLException e) {
            e.printStackTrace();
        } catch (java.io.IOException e) {
            e.printStackTrace();
        } finally {
            if (null != br) {
                try {
                    br.close();
                } catch (java.io.IOException e) {
                    e.printStackTrace();
                }
            }
            if (null != is) {
                try {
                    is.close();
                } catch (java.io.IOException e) {
                    e.printStackTrace();
                }
            }
            if(connection!=null){
                connection.disconnect();
            }
        }
        return map;
    }*/


}
