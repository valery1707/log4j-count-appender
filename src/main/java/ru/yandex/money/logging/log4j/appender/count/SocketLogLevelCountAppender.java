package ru.yandex.money.logging.log4j.appender.count;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender;
import org.apache.logging.log4j.core.appender.SocketAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAliases;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.layout.SerializedLayout;
import org.apache.logging.log4j.core.net.AbstractSocketManager;
import org.apache.logging.log4j.core.net.Protocol;
import org.apache.logging.log4j.core.net.ssl.SslConfiguration;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * Аппендер к log4j, агрегирующий логи по уровню (level) и отправляющий метрику с их количеством в сокет.
 *
 * @author churkin
 * @since 19.09.2018
 */
@Plugin(name = "SocketLogLevelCount", category = "Core", elementType = Appender.ELEMENT_TYPE, printObject = true)
public class SocketLogLevelCountAppender extends SocketAppender {

    private static final long DEFAULT_SEND_PERIOD_MS = 1000;

    /**
     * level -> счетчик событий по этому level
     */
    private final Map<Level, LongAdder> counterMap;
    /**
     * Период ожидания остановки аппендера
     */
    private final long shutdownTimeoutMs;
    /**
     * Поток, занимающийся отправкой количества событий в сокет
     */
    private final SenderThread senderThread;

    private SocketLogLevelCountAppender(String name,
                                        Layout<? extends Serializable> layout,
                                        Filter filter,
                                        boolean ignoreExceptions,
                                        long shutdownTimeoutMs,
                                        long sendPeriodMs,
                                        boolean immediateFlush,
                                        AbstractSocketManager manager) {
        super(name, layout, filter, manager, ignoreExceptions, immediateFlush, null);
        this.counterMap = Arrays.stream(Level.values())
                .collect(Collectors.toMap(level -> level, level -> new LongAdder()));
        this.shutdownTimeoutMs = shutdownTimeoutMs;
        this.senderThread = new SenderThread(counterMap, sendPeriodMs, LOGGER, this::writeByteArrayToManager);
    }

    @Override
    public void start() {
        senderThread.start();
        super.start();
    }

    @Override
    public boolean stop(final long timeout, final TimeUnit timeUnit) {
        setStopping();
        super.stop(timeout, timeUnit, false);
        LOGGER.trace("SocketCountAppender stopping");
        senderThread.shutdown();
        try {
            senderThread.join(shutdownTimeoutMs);
        } catch (final InterruptedException ex) {
            LOGGER.warn("Interrupted while stopping SocketCountAppender {}", getName());
        }
        LOGGER.trace("SocketCountAppender stopped");
        setStopped();
        return true;
    }

    @Override
    public void append(final LogEvent logEvent) {
        if (!isStarted()) {
            throw new IllegalStateException("SocketCountAppender " + getName() + " is not active");
        }
        counterMap.get(logEvent.getLevel()).increment();
    }

    @PluginBuilderFactory
    public static <B extends Builder<B>> B createBuilder() {
        return new Builder<B>().asBuilder();
    }

    /**
     * Subclasses can extend this abstract Builder.
     *
     * @param <B> This builder class.
     */
    public static class Builder<B extends Builder<B>> extends AbstractOutputStreamAppender.Builder<B>
            implements org.apache.logging.log4j.core.util.Builder<SocketLogLevelCountAppender> {

        @PluginBuilderAttribute
        private int connectTimeout;

        @PluginBuilderAttribute
        private String host = "localhost";

        @PluginBuilderAttribute
        private boolean immediateFail = true;

        @PluginBuilderAttribute
        private int port;

        @PluginBuilderAttribute
        private Protocol protocol = Protocol.UDP;

        @PluginBuilderAttribute
        @PluginAliases({"reconnectDelay, delayMillis"})
        private int reconnectDelayMillis;

        @PluginElement("SslConfiguration")
        @PluginAliases({"SslConfig"})
        private SslConfiguration sslConfiguration;

        @PluginBuilderAttribute
        private long shutdownTimeout = 0L;

        @PluginBuilderAttribute
        private long sendPeriod = DEFAULT_SEND_PERIOD_MS;

        @SuppressWarnings("resource")
        @Override
        public SocketLogLevelCountAppender build() {
            Layout<? extends Serializable> layout = getLayout();
            if (layout == null) {
                layout = SerializedLayout.createLayout();
            }

            String name = getName();
            if (name == null) {
                SocketLogLevelCountAppender.LOGGER.error("No name provided for SocketCountAppender");
                return null;
            }

            boolean immediateFlush = isImmediateFlush();
            Protocol actualProtocol = protocol != null ? protocol : Protocol.UDP;
            if (actualProtocol == Protocol.UDP) {
                immediateFlush = true;
            }

            if (sendPeriod <= 0) {
                SocketLogLevelCountAppender.LOGGER.error("Illegal sendPeriod for SocketCountAppender: {}", sendPeriod);
                return null;
            }

            AbstractSocketManager manager = createSocketManager(name, actualProtocol, host, port,
                    connectTimeout, sslConfiguration, reconnectDelayMillis, immediateFail, layout, getBufferSize());

            return new SocketLogLevelCountAppender(name, layout, getFilter(), isIgnoreExceptions(), shutdownTimeout,
                    sendPeriod, !isBufferedIo() || immediateFlush, manager);
        }

        public int getConnectTimeout() {
            return connectTimeout;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public Protocol getProtocol() {
            return protocol;
        }

        public SslConfiguration getSslConfiguration() {
            return sslConfiguration;
        }

        public boolean getImmediateFail() {
            return immediateFail;
        }

        public B withConnectTimeout(final int connectTimeout) {
            this.connectTimeout = connectTimeout;
            return asBuilder();
        }

        public B withHost(final String host) {
            this.host = host;
            return asBuilder();
        }

        public B withImmediateFail(final boolean immediateFail) {
            this.immediateFail = immediateFail;
            return asBuilder();
        }

        public B withPort(final int port) {
            this.port = port;
            return asBuilder();
        }

        public B withProtocol(final Protocol protocol) {
            this.protocol = protocol;
            return asBuilder();
        }

        public B withReconnectDelayMillis(final int reconnectDelayMillis) {
            this.reconnectDelayMillis = reconnectDelayMillis;
            return asBuilder();
        }

        public B withSslConfiguration(final SslConfiguration sslConfiguration) {
            this.sslConfiguration = sslConfiguration;
            return asBuilder();
        }

        public B withShutdownTimeout(long shutdownTimeout) {
            this.shutdownTimeout = shutdownTimeout;
            return asBuilder();
        }

        public B withSendPeriod(long sendPeriod) {
            this.sendPeriod = sendPeriod;
            return asBuilder();
        }
    }

}
