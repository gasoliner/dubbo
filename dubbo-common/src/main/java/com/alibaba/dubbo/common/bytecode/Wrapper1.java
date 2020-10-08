//package com.alibaba.dubbo.common.bytecode;
//
///**
// * @author wanhongji
// * @date 2020-10-08 13:14
// */
//public class Wrapper1 extends com.alibaba.dubbo.common.bytecode.Wrapper {
//
//    public static String[] pns;
//
//    public static java.util.Map pts;
//
//    public static String[] mns;
//
//    public static String[] dmns;
//
//    public static Class[] mts0;
//
//    public String[] getPropertyNames() {
//        return pns;
//    }
//
//    public boolean hasProperty(String n) {
//        return pts.containsKey($1);
//    }
//
//    public Class getPropertyType(String n) {
//        return (Class) pts.get($1);
//    }
//
//    public String[] getMethodNames() {
//        return mns;
//    }
//
//    public String[] getDeclaredMethodNames() {
//        return dmns;
//    }
//
//    public void setPropertyValue(Object o, String n, Object v) {
//        com.wan.comeon.dubbo.provider.UserServiceImpl w;
//        try {
//            w = ((com.wan.comeon.dubbo.provider.UserServiceImpl) $1);
//        } catch (Throwable e) {
//            throw new IllegalArgumentException(e);
//        }
//        throw new org.apache.dubbo.common.bytecode.NoSuchPropertyException("Not found property \"" + $2 + "\" field or setter method in class com.wan.comeon.dubbo.provider.UserServiceImpl.");
//    }
//
//    public Object getPropertyValue(Object o, String n) {
//        com.wan.comeon.dubbo.provider.UserServiceImpl w;
//        try {
//            w = ((com.wan.comeon.dubbo.provider.UserServiceImpl) $1);
//        } catch (Throwable e) {
//            throw new IllegalArgumentException(e);
//        }
//        throw new org.apache.dubbo.common.bytecode.NoSuchPropertyException("Not found property \"" + $2 + "\" field or setter method in class com.wan.comeon.dubbo.provider.UserServiceImpl.");
//    }
//
//    // proxy 是被包装的类的实例对象, methodName 是methodName, parameterTypes 是parameterTypes, arguments 是arguments
//    public Object invokeMethod(Object proxy, String methodName, Class[] parameterTypes, Object[] arguments) throws java.lang.reflect.InvocationTargetException {
//        com.wan.comeon.dubbo.provider.UserServiceImpl w;
//        try {
//            w = ((com.wan.comeon.dubbo.provider.UserServiceImpl) proxy);
//        } catch (Throwable e) {
//            throw new IllegalArgumentException(e);
//        }
//        try {
//            if ("introduceYourSelf".equals(methodName) && parameterTypes.length == 0) {
//                return ($w) w.introduceYourSelf();
//            }
//        } catch (Throwable e) {
//            throw new java.lang.reflect.InvocationTargetException(e);
//        }
//        throw new org.apache.dubbo.common.bytecode.NoSuchMethodException("Not found method \"" + $2 + "\" in class com.wan.comeon.dubbo.provider.UserServiceImpl.");
//    }
//}
