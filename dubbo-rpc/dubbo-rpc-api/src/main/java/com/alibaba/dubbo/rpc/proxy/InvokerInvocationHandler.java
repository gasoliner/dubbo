/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.dubbo.rpc.proxy;

import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.RpcInvocation;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * InvokerHandler
 */

/**
 * consumer调用的起始点
 *  dubbo会对每个consumer类使用（拼接proxy类代码+javassist编译）的方式实现一个代理类
 *  代理类实现 api-interface，在实现方法上会调用 本类中的 invoke方法，所以这里的invoke是consumer调用的起始点
 *
 *  调用栈如下：
     * introduceYourSelf:-1, proxy0 
         * invoke:74, InvokerInvocationHandler 
             * invoke:82, MockClusterInvoker 
                * invoke:92, AbstractCluster$InterceptorInvokerNode 
                    * intercept:47, ClusterInterceptor 
                        * invoke:259, AbstractClusterInvoker 
                             * doInvoke:82, FailoverClusterInvoker 
                                 * invoke:56, InvokerWrapper 
                                     * invoke:78, ListenerInvokerWrapper 
                                         * invoke:81, ProtocolFilterWrapper$1 
                                             * invoke:55, ConsumerContextFilter 
                                                 * invoke:81, ProtocolFilterWrapper$1 
                                                    * invoke:51, FutureFilter 
                                                        * invoke:81, ProtocolFilterWrapper$1 
                                                            * invoke:89, MonitorFilter 
                                                                 * invoke:52, AsyncToSyncInvoker
                                                                     * invoke:162, AbstractInvoker
                                                                        * doInvoke:79, DubboInvoker
 */
public class InvokerInvocationHandler implements InvocationHandler {

    private final Invoker<?> invoker;

    public InvokerInvocationHandler(Invoker<?> handler) {
        this.invoker = handler;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(invoker, args);
        }
        //本地方法直接在本地执行
        if ("toString".equals(methodName) && parameterTypes.length == 0) {
            return invoker.toString();
        }
        if ("hashCode".equals(methodName) && parameterTypes.length == 0) {
            return invoker.hashCode();
        }
        if ("equals".equals(methodName) && parameterTypes.length == 1) {
            return invoker.equals(args[0]);
        }
        // 这里的 invoker 指向 MockClusterInvoker
        return invoker.invoke(new RpcInvocation(method, args)).recreate();
    }

}
