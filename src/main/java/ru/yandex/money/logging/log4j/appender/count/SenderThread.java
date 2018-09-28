package ru.yandex.money.logging.log4j.appender.count;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.util.Log4jThread;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * Поток, занимающийся отправкой количества событий в сокет.
 *
 * @author churkin
 * @since 27.09.2018
 */
class SenderThread extends Log4jThread {

    private static final AtomicLong SENDER_THREAD_SEQUENCE = new AtomicLong(1);

    private volatile boolean shutdown = false;
    /**
     * level -> счетчик событий по этому level
     */
    @Nonnull
    private final Map<Level, LongAdder> counterMap;
    /**
     * Период между отправками количества событий в сокет
     */
    private final long periodMs;
    @Nonnull
    private final Logger log;
    /**
     * Действие по отправке количества событий в сокет
     */
    @Nonnull
    private final Consumer<CountLogEvent> writeEventConsumer;

    SenderThread(@Nonnull Map<Level, LongAdder> counterMap,
                 long periodMs,
                 @Nonnull Logger log,
                 @Nonnull Consumer<CountLogEvent> writeEventConsumer) {
        super("socket-sender-" + SENDER_THREAD_SEQUENCE.getAndIncrement());
        this.counterMap = requireNonNull(counterMap, "counterMap");
        this.periodMs = periodMs;
        this.log = requireNonNull(log, "log");
        this.writeEventConsumer = requireNonNull(writeEventConsumer, "writeEventConsumer");
        setDaemon(true);
    }

    @Override
    public void run() {
        long lastTimeMs = now();
        while (!shutdown) {
            long currentTimeMs = now();
            long scheduledTimeMs = lastTimeMs + periodMs;
            if (currentTimeMs < scheduledTimeMs) {
                try {
                    Thread.sleep(scheduledTimeMs - currentTimeMs);
                } catch (InterruptedException e) {
                    break;
                }
            }
            lastTimeMs = now();
            writeEventsToSocket();
        }
        // Process any remaining items in the queue.
        log.trace("SenderThread shutting down. Processing remaining events: {}", counterMap);
        writeEventsToSocket();
        log.trace("SenderThread stopped. Queue has events remaining: {}", counterMap);
    }

    private long now() {
        return System.currentTimeMillis();
    }

    private void writeEventsToSocket() {
        for (Map.Entry<Level, LongAdder> entry : counterMap.entrySet()) {
            LongAdder counter = entry.getValue();
            // здесь не гарантируется точное значение count, оно может быть несколько меньше реального значения в момент reset,
            // если идут конкуррентные обновления счетчика
            long count = counter.sumThenReset();
            if (count == 0) {
                continue;
            }
            try {
                writeEventConsumer.accept(new CountLogEvent(entry.getKey(), count));
            } catch (RuntimeException e) {
                log.error("SenderThread failed to process events", e);
            }
        }
    }

    /**
     * Остановка потока
     */
    void shutdown() {
        shutdown = true;
        if (getState() == Thread.State.TIMED_WAITING || getState() == Thread.State.WAITING) {
            this.interrupt();
        }
    }
}