package ru.yandex.money.logging.log4j.appender.count;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.AbstractLogEvent;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.net.Protocol;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.apache.logging.log4j.util.SortedArrayStringMap;
import org.testng.annotations.Test;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author churkin
 * @since 26.09.2018
 */
public class SocketLogLevelCountAppenderTest {

    private static final InetAddress HOST;
    static {
        try {
            HOST = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    private static final AtomicInteger PORT = new AtomicInteger(11111);

    @Test
    public void test_differentLevels() throws Exception {
        int port = PORT.incrementAndGet();
        SocketLogLevelCountAppender appender = SocketLogLevelCountAppender.createBuilder()
                .withName("name")
                .withHost(HOST.getHostAddress())
                .withPort(port)
                .withProtocol(Protocol.UDP)
                .withSendPeriod(10)
                .withLayout(PatternLayout.newBuilder()
                        .withPattern("log.%level.%X{count}|") // log.INFO.3|
                        .build())
                .build();
        appender.start();

        Map<Level, LongAdder> counterMap = new ConcurrentHashMap<>();
        Arrays.stream(Level.values()).forEach(level -> counterMap.put(level, new LongAdder()));

        UdpServer udpServer = new UdpServer(HOST, port, message -> {
            LevelAndCount levelAndCount = getLevelAndCount(message);
            counterMap.get(levelAndCount.getLevel()).add(levelAndCount.getCount());
        });
        udpServer.start();

        appender.append(createLogEvent(Level.INFO));
        appender.append(createLogEvent(Level.WARN));
        appender.append(createLogEvent(Level.INFO));
        appender.append(createLogEvent(Level.INFO));

        Thread.sleep(50);

        udpServer.stop();
        appender.stop(100, TimeUnit.MILLISECONDS);

        assertEquals(counterMap.get(Level.INFO).sum(), 3L);
        assertEquals(counterMap.get(Level.WARN).sum(), 1L);
    }

    @Test(description = "Проверяем обработку событий: 100000 событий в 10 потоков за 100мс с потерей не более 0.01% событий",
            invocationCount = 20)
    public void test_highload() throws Exception {
        double maxLossRate = 0.0001;
        int numberOfThreads = 10;
        int eventsPerThread = 10000;
        long timeout = 100;

        int port = PORT.incrementAndGet();
        SocketLogLevelCountAppender appender = SocketLogLevelCountAppender.createBuilder()
                .withName("name")
                .withHost(HOST.getHostAddress())
                .withPort(port)
                .withProtocol(Protocol.UDP)
                .withSendPeriod(10)
                .withLayout(PatternLayout.newBuilder()
                        .withPattern("log.%level.%X{count}|") // log.INFO.3|
                        .build())
                .build();
        appender.start();

        LongAdder counter = new LongAdder();
        UdpServer udpServer = new UdpServer(HOST, port, message -> {
            long count = getLevelAndCount(message).getCount();
            counter.add(count);
        });
        udpServer.start();

        long totalEvents = eventsPerThread * numberOfThreads;
        long maxPossiblyLostEvents = (long) (totalEvents * maxLossRate);

        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        long startTime = System.currentTimeMillis();
        for (long i = 0; i < numberOfThreads; i++) {
            executorService.submit(() -> {
                for (int j = 0; j < eventsPerThread; j++) {
                    appender.append(createLogEvent(Level.INFO));
                }
            });
        }

        while (System.currentTimeMillis() - startTime < timeout) {
            Thread.sleep(10);
            if (counter.sum() >= totalEvents) {
                long time = System.currentTimeMillis() - startTime;
                System.out.println("Time: " + time + "ms, events: " + counter.sum());
                break;
            }
        }

        executorService.shutdown();
        udpServer.stop();
        appender.stop(100, TimeUnit.MILLISECONDS);

        assertTrue(totalEvents - counter.sum() <= maxPossiblyLostEvents,
                String.format("total: %d, lost: %d", totalEvents, totalEvents - counter.sum()));
    }

    private static LogEvent createLogEvent(Level level) {
        return new FakeLogEvent(level);
    }

    // log.INFO.3|
    @Nonnull
    private LevelAndCount getLevelAndCount(@Nonnull String message) {
        String[] parts = message.split("\\.");
        if (parts.length != 3) {
            throw new RuntimeException("Invalid message: " + message);
        }
        Level level = Level.valueOf(parts[1]);
        Long count = Long.valueOf(parts[2].substring(0, parts[2].length() - 1));
        return new LevelAndCount(level, count);
    }

    private static class LevelAndCount {
        private final Level level;
        private final long count;

        LevelAndCount(Level level, long count) {
            this.level = level;
            this.count = count;
        }

        public Level getLevel() {
            return level;
        }

        public long getCount() {
            return count;
        }
    }

    private static class UdpServer {
        private final InetAddress host;
        private final int port;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private DatagramSocket socket;
        private Thread udpServerThread;
        private final Consumer<String> messageConsumer;

        public UdpServer(InetAddress host, int port, Consumer<String> messageConsumer) {
            this.host = host;
            this.port = port;
            this.messageConsumer = messageConsumer;
        }

        public void start() {
            try {
                socket = new DatagramSocket(port, host);
            } catch (SocketException e) {
                throw new RuntimeException(e);
            }
            udpServerThread = new Thread(() -> {
                try {
                    while (!closed.get()) {
                        byte[] buffer = new byte[128];
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);
                        String message = new String(packet.getData(), 0, packet.getLength());
                        messageConsumer.accept(message);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            });
            udpServerThread.start();
        }

        public void stop() {
            closed.set(true);
            socket.close();
            try {
                udpServerThread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class FakeLogEvent extends AbstractLogEvent {

        private static final long serialVersionUID = 1L;

        @Nonnull
        private final Level level;

        public FakeLogEvent(Level level) {
            this.level = level;
        }

        @Override
        @Nonnull
        public Level getLevel() {
            return level;
        }

        @Override
        public Message getMessage() {
            return new SimpleMessage();
        }

        @Override
        public ReadOnlyStringMap getContextData() {
            return new SortedArrayStringMap();
        }
    }
}
