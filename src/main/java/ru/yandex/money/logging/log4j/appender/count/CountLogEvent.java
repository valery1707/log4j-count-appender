package ru.yandex.money.logging.log4j.appender.count;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.AbstractLogEvent;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.apache.logging.log4j.util.SortedArrayStringMap;

import javax.annotation.Nonnull;

import static java.util.Objects.requireNonNull;

/**
 * Событие, содержащее количество записей логов на определеном level.
 *
 * @author churkin
 * @since 26.09.2018
 */
class CountLogEvent extends AbstractLogEvent {

    /**
     * Название параметра в контексте, содержащее число записей лога на этом уровне level.
     * Можно его получить через {@link org.apache.logging.log4j.core.layout.PatternLayout} следующим образом: %X{count}
     */
    private static final String COUNT_FIELD_NAME = "count";

    @Nonnull
    private final Level level;
    private final SortedArrayStringMap contextData;

    CountLogEvent(@Nonnull Level level, long count) {
        this.level = requireNonNull(level, "level");
        this.contextData = new SortedArrayStringMap();
        this.contextData.putValue(COUNT_FIELD_NAME, Long.toString(count));
    }

    @Override
    public ReadOnlyStringMap getContextData() {
        return contextData;
    }

    @Override
    public Level getLevel() {
        return level;
    }

}
