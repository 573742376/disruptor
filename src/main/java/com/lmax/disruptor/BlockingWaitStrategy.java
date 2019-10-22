/*
 * Copyright 2011 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lmax.disruptor;

import com.lmax.disruptor.util.ThreadHints;

/**
 * Blocking strategy that uses a lock and condition variable for {@link EventProcessor}s waiting on a barrier.
 * <p>
 * This strategy can be used when throughput and low-latency are not as important as CPU resource.
 */
public final class BlockingWaitStrategy implements WaitStrategy
{
    private final Object mutex = new Object();

    @Override
    public long waitFor(long sequence, Sequence cursorSequence, Sequence dependentSequence, SequenceBarrier barrier)
        throws AlertException, InterruptedException
    {
        long availableSequence;
        //消费者消费大于生产者
        if (cursorSequence.get() < sequence)
        {
        	
            synchronized (mutex)
            {
            	//为什么这里时循环判断等待了,就是因为生产者每生产发布一个消息都会唤醒
            	//而在唤醒的时候 这里未必就是false
                while (cursorSequence.get() < sequence)
                {
                    barrier.checkAlert();
                    mutex.wait();
                }
            }
        }

        //如果时第一组就是生产者 上面的判断了这里肯定能通过
        //如果这里时依赖上一组的消费者,这里就是上一组的消费记录,当前组一定要在上一组消费完了才能消费
        //确保在之后才能执行
        while ((availableSequence = dependentSequence.get()) < sequence)
        {
            barrier.checkAlert();
            ThreadHints.onSpinWait();
        }

        //返回最新的生产序号或者上一组消费过的并且大于sequence的
        //也就是说这里不一定是大于1 加入要获取3 又可能返回 5
        return availableSequence;
    }

    @Override
    public void signalAllWhenBlocking()
    {
        synchronized (mutex)
        {
        	//发布的时候唤醒等待的消费者 可以消费了
            mutex.notifyAll();
        }
    }

    @Override
    public String toString()
    {
        return "BlockingWaitStrategy{" +
            "mutex=" + mutex +
            '}';
    }
}
