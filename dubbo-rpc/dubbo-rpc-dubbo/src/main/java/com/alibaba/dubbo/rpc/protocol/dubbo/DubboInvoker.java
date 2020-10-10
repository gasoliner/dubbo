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
package com.alibaba.dubbo.rpc.protocol.dubbo;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.utils.AtomicPositiveInteger;
import com.alibaba.dubbo.common.utils.ConfigUtils;
import com.alibaba.dubbo.remoting.RemotingException;
import com.alibaba.dubbo.remoting.TimeoutException;
import com.alibaba.dubbo.remoting.exchange.ExchangeClient;
import com.alibaba.dubbo.remoting.exchange.ResponseFuture;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.RpcInvocation;
import com.alibaba.dubbo.rpc.RpcResult;
import com.alibaba.dubbo.rpc.protocol.AbstractInvoker;
import com.alibaba.dubbo.rpc.support.RpcUtils;

import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

/**
 * DubboInvoker
 */

/**
 *
 * 官方文档：https://dubbo.apache.org/zh-cn/docs/source_code_guide/service-invoking-process.html
 *
 * 官方以 DemoService 为例，调用图如下：
 *  proxy0#sayHello(String)//动态生成的代理类
 *   —> InvokerInvocationHandler#invoke(Object, Method, Object[])
 *     —> MockClusterInvoker#invoke(Invocation)//执行服务降级逻辑修改逻辑
 *       —> AbstractClusterInvoker#invoke(Invocation)
 *         —> FailoverClusterInvoker#doInvoke(Invocation, List<Invoker<T>>, LoadBalance)
 *           —> Filter#invoke(Invoker, Invocation)  // 包含多个 Filter 调用
 *             —> ListenerInvokerWrapper#invoke(Invocation)
 *               —> AbstractInvoker#invoke(Invocation)
 *                 —> DubboInvoker#doInvoke(Invocation)
 *                   —> ReferenceCountExchangeClient#request(Object, int)// Client被引用次数计数
 *                     —> HeaderExchangeClient#request(Object, int) //心跳相关逻辑
 *                       —> HeaderExchangeChannel#request(Object, int)
 *                         —> AbstractPeer#send(Object)
 *                           —> AbstractClient#send(Object, boolean) // 框架方法，做一些 发送前的准备工作，XXXClient持有XXXChannel
 *                             —> NettyChannel#send(Object, boolean) // 调用 netty的channel的writeAndFlush方法
 *                               —> NioClientSocketChannel#write(Object)
 * @param <T>
 */
public class DubboInvoker<T> extends AbstractInvoker<T> {

    private final ExchangeClient[] clients;

    private final AtomicPositiveInteger index = new AtomicPositiveInteger();

    private final String version;

    private final ReentrantLock destroyLock = new ReentrantLock();

    private final Set<Invoker<?>> invokers;

    public DubboInvoker(Class<T> serviceType, URL url, ExchangeClient[] clients) {
        this(serviceType, url, clients, null);
    }

    public DubboInvoker(Class<T> serviceType, URL url, ExchangeClient[] clients, Set<Invoker<?>> invokers) {
        super(serviceType, url, new String[]{Constants.INTERFACE_KEY, Constants.GROUP_KEY, Constants.TOKEN_KEY, Constants.TIMEOUT_KEY});
        this.clients = clients;
        // get version.
        this.version = url.getParameter(Constants.VERSION_KEY, "0.0.0");
        this.invokers = invokers;
    }

    @Override
    protected Result doInvoke(final Invocation invocation) throws Throwable {
        RpcInvocation inv = (RpcInvocation) invocation;
        final String methodName = RpcUtils.getMethodName(invocation);
        inv.setAttachment(Constants.PATH_KEY, getUrl().getPath());
        inv.setAttachment(Constants.VERSION_KEY, version);

        //默认计算出的 currentClient 为 ReferenceCountExchangeClient实例
        ExchangeClient currentClient;
        if (clients.length == 1) {
            currentClient = clients[0];
        } else {
            currentClient = clients[index.getAndIncrement() % clients.length];
        }
        try {
            /**
             * dubbo的三种调用方式：
             * 1.异步无返回值（OneWay）
             * 2.异步有返回值
             * 3.同步阻塞
             *
             * 其中异步有返回值和同步阻塞的区别在于future.get由谁调用：
             *      异步有返回值 是用户自己调用 future.get() 方法，使用方法：
             *          String r = demoService.sayHello();//这个返回值是空的，不要用
             *          //需要自己调用RpcContext获取Future
             *          Future<String> future = RpcContext.getContext().getFuture();
             *          //去做点别的浪费时间的业务逻辑
             *          future.get()//阻塞获取异步结果
             *      同步阻塞是由 Dubbo框架调用 future.get() 方法，使用方法略
             */
            boolean isAsync = RpcUtils.isAsync(getUrl(), invocation);
            boolean isOneway = RpcUtils.isOneway(getUrl(), invocation);
            int timeout = getUrl().getMethodParameter(methodName, Constants.TIMEOUT_KEY, Constants.DEFAULT_TIMEOUT);
            if (isOneway) {
                /**
                 * isSent：是否等待消息发出：
                 *  1.false：不等待消息发出，将消息放到IO队列，即可返回
                 *  2.true：等待消息发出
                 */
                boolean isSent = getUrl().getMethodParameter(methodName, Constants.SENT_KEY, false);
                currentClient.send(inv, isSent);
                RpcContext.getContext().setFuture(null);
                return new RpcResult();
            } else if (isAsync) {
                ResponseFuture future = currentClient.request(inv, timeout);
                /**
                 * 这里将 dubbo的future使用FutureAdapter与JDK的Future进行适配
                 * 所以用户在调用 RpcContext.getContext().getFuture();获得的Future是JDK的Future
                 */
                RpcContext.getContext().setFuture(new FutureAdapter<Object>(future));
                //在这里暂时返回了空的结果
                return new RpcResult();
            } else {
                RpcContext.getContext().setFuture(null);
                return (Result) currentClient.request(inv, timeout).get();
            }
        } catch (TimeoutException e) {
            throw new RpcException(RpcException.TIMEOUT_EXCEPTION, "Invoke remote method timeout. method: " + invocation.getMethodName() + ", provider: " + getUrl() + ", cause: " + e.getMessage(), e);
        } catch (RemotingException e) {
            throw new RpcException(RpcException.NETWORK_EXCEPTION, "Failed to invoke remote method: " + invocation.getMethodName() + ", provider: " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isAvailable() {
        if (!super.isAvailable())
            return false;
        for (ExchangeClient client : clients) {
            if (client.isConnected() && !client.hasAttribute(Constants.CHANNEL_ATTRIBUTE_READONLY_KEY)) {
                //cannot write == not Available ?
                return true;
            }
        }
        return false;
    }

    @Override
    public void destroy() {
        // in order to avoid closing a client multiple times, a counter is used in case of connection per jvm, every
        // time when client.close() is called, counter counts down once, and when counter reaches zero, client will be
        // closed.
        if (super.isDestroyed()) {
            return;
        } else {
            // double check to avoid dup close
            destroyLock.lock();
            try {
                if (super.isDestroyed()) {
                    return;
                }
                super.destroy();
                if (invokers != null) {
                    invokers.remove(this);
                }
                for (ExchangeClient client : clients) {
                    try {
                        client.close(ConfigUtils.getServerShutdownTimeout());
                    } catch (Throwable t) {
                        logger.warn(t.getMessage(), t);
                    }
                }

            } finally {
                destroyLock.unlock();
            }
        }
    }
}
