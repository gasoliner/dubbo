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
package com.alibaba.dubbo.remoting.transport.dispatcher;

import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.remoting.Channel;
import com.alibaba.dubbo.remoting.ChannelHandler;

public class ChannelEventRunnable implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ChannelEventRunnable.class);

    private final ChannelHandler handler;
    private final Channel channel;
    private final ChannelState state;
    private final Throwable exception;
    private final Object message;

    public ChannelEventRunnable(Channel channel, ChannelHandler handler, ChannelState state) {
        this(channel, handler, state, null);
    }

    public ChannelEventRunnable(Channel channel, ChannelHandler handler, ChannelState state, Object message) {
        this(channel, handler, state, message, null);
    }

    public ChannelEventRunnable(Channel channel, ChannelHandler handler, ChannelState state, Throwable t) {
        this(channel, handler, state, null, t);
    }

    public ChannelEventRunnable(Channel channel, ChannelHandler handler, ChannelState state, Object message, Throwable exception) {
        this.channel = channel;
        this.handler = handler;
        this.state = state;
        this.message = message;
        this.exception = exception;
    }

    /**
     * 事件处理的新起点(不过他也是个中转站)
     *
     * 这里的调用栈如下：
     * ChannelEventRunnable#run()
     *   —> DecodeHandler#received(Channel, Object)//实现在线程池线程中进行消息体解码(调用 Decodeable#decode())
     *     —> HeaderExchangeHandler#received(Channel, Object)//判断是数据包类型，中转站向后调用服务
     *       —> HeaderExchangeHandler#handleRequest(ExchangeChannel, Request)//中转站，调用ExchangeHandlerAdapter#reply
     *         —> DubboProtocol.requestHandler#reply(ExchangeChannel, Object)//获取invoker实例，调用invoker.invoke()
     *           —> Filter#invoke(Invoker, Invocation)//责任链模式的filter chain
     *             —> AbstractProxyInvoker#invoke(Invocation)
     *               —> Wrapper0#invokeMethod(Object, String, Class[], Object[])//由JavassistProxyFactory生成继承于
     *                                                                          //Wrapper类的代理类，在该类的invokeMethod中
     *                                                                          //调用demoService#sayHello()【也是通过拼接原始code，然后javassist.toClass的方式】
     *                 —> DemoServiceImpl#sayHello(String)
     */
    @Override
    public void run() {
        //一个ChannelEventRunnable被创建后，接到的事件99.999%都是RECEIVED，所以这里利用了CPU的分支预测
        //提升CPU运行效率
        //官方博客文章：https://dubbo.apache.org/zh-cn/blog/optimization-branch-prediction.html
        if (state == ChannelState.RECEIVED) {
            try {
                // 将 channel 和 message 传给 ChannelHandler 对象，进行后续的调用
                // 这里的handler指向 DecoderHandler
                handler.received(channel, message);
            } catch (Exception e) {
                logger.warn("ChannelEventRunnable handle " + state + " operation error, channel is " + channel
                        + ", message is " + message, e);
            }
        } else {
            switch (state) {
            case CONNECTED:
                try {
                    handler.connected(channel);
                } catch (Exception e) {
                    logger.warn("ChannelEventRunnable handle " + state + " operation error, channel is " + channel, e);
                }
                break;
            case DISCONNECTED:
                try {
                    handler.disconnected(channel);
                } catch (Exception e) {
                    logger.warn("ChannelEventRunnable handle " + state + " operation error, channel is " + channel, e);
                }
                break;
            case SENT:
                try {
                    handler.sent(channel, message);
                } catch (Exception e) {
                    logger.warn("ChannelEventRunnable handle " + state + " operation error, channel is " + channel
                            + ", message is " + message, e);
                }
            case CAUGHT:
                try {
                    handler.caught(channel, exception);
                } catch (Exception e) {
                    logger.warn("ChannelEventRunnable handle " + state + " operation error, channel is " + channel
                            + ", message is: " + message + ", exception is " + exception, e);
                }
                break;
            default:
                logger.warn("unknown state: " + state + ", message is " + message);
            }
        }

    }

    /**
     * ChannelState
     *
     *
     */
    public enum ChannelState {

        /**
         * CONNECTED
         */
        CONNECTED,

        /**
         * DISCONNECTED
         */
        DISCONNECTED,

        /**
         * SENT
         */
        SENT,

        /**
         * RECEIVED
         */
        RECEIVED,

        /**
         * CAUGHT
         */
        CAUGHT
    }

}
