package com.venus.nacos.extend;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

public class VenusHostnameVerifier implements HostnameVerifier {

    @Override
    public boolean verify(String hostname, SSLSession session) {
        return true;
    }


}
