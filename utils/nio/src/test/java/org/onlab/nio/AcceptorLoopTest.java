package org.onlab.nio;

import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;
import static org.onlab.junit.TestTools.delay;

/**
 * Unit tests for AcceptLoop.
 */
public class AcceptorLoopTest extends AbstractLoopTest {

    private static final int PORT = 9876;

    private static final SocketAddress SOCK_ADDR = new InetSocketAddress("127.0.0.1", PORT);

    private static class MyAcceptLoop extends AcceptorLoop {
        private final CountDownLatch loopStarted = new CountDownLatch(1);
        private final CountDownLatch loopFinished = new CountDownLatch(1);
        private final CountDownLatch runDone = new CountDownLatch(1);
        private final CountDownLatch ceaseLatch = new CountDownLatch(1);

        private int acceptCount = 0;

        MyAcceptLoop() throws IOException {
            super(500, SOCK_ADDR);
        }

        @Override
        protected void acceptConnection(ServerSocketChannel ssc) throws IOException {
            acceptCount++;
        }

        @Override
        public void loop() throws IOException {
            loopStarted.countDown();
            super.loop();
            loopFinished.countDown();
        }

        @Override
        public void run() {
            super.run();
            runDone.countDown();
        }

        @Override
        public void shutdown() {
            super.shutdown();
            ceaseLatch.countDown();
        }
    }

    @Test
//    @Ignore("Doesn't shut down the socket")
    public void basic() throws IOException {
        MyAcceptLoop myAccLoop = new MyAcceptLoop();
        AcceptorLoop accLoop = myAccLoop;
        exec.execute(accLoop);
        waitForLatch(myAccLoop.loopStarted, "loopStarted");
        delay(200); // take a quick nap
        accLoop.shutdown();
        waitForLatch(myAccLoop.loopFinished, "loopFinished");
        waitForLatch(myAccLoop.runDone, "runDone");
        assertEquals(0, myAccLoop.acceptCount);
    }
}
