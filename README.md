## Appender для подсчета количества логов и отправки в сокет

SocketLogLevelCountAppender собирает статистику количества событий **LogEvent**, агрегируя по уровню **level**.
С заданной периодичностью происходит запись в сокет событий, содержащих уровень логирования **level** 
и количество событий с момента последней записи в сокет.

**Важно!** Статистика количества событий не гарантирует 100% точность, следует ориентироваться на погрешность в 0.01% 
при нагрузке в 100000 событий за 100мс по одному level. Это обусловлено использованием LongAdder#sumThenReset().

Формат сообщений, попадающих в сокет, следует задавать с помощью PatternLayout, в шаблоне которого
можно использовать параметры **%level** и **%X{count}**. 

Здесь приведен пример конфигурации для отправки метрик в statsd по UDP:
```$xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration ... packages="ru.yandex.money.logging.log4j.appender">
    <Appenders>
        <SocketLogLevelCount name="STATSD" host="127.0.0.1" port="8125" protocol="UDP">
            <PatternLayout pattern="kassa.logs.%level:%X{count}|c"/>
        </SocketLogLevelCount>
    </Appenders>
    <Loggers>
        <Root level="INFO">
            <AppenderRef ref="STDOUT"/>
        </Root>
    </Loggers>
</Configuration>
```

Список возможных параметров элемента SocketLogLevelCount:
- connectTimeout - таймаут установки соединения, мс
- host - хост для отправки пакетов
- port - порт для отправки пакетов
- protocol - UDP или TCP
- sendPeriod - период отправки, мс