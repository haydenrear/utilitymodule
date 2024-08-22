package com.hayden.utilitymodule.proxies;

import lombok.experimental.UtilityClass;

import java.lang.reflect.Proxy;

@UtilityClass
public class ProxyUtil {

    public boolean isProxy(Object obj) {
        if (obj instanceof Class c)
            throw new RuntimeException("Attempted to check if class class was proxy class!");
        return Proxy.isProxyClass(obj.getClass());
    }



}
