[![Build Status](https://travis-ci.org/yandex-money-tech/log4j-count-appender.svg?branch=master)](https://travis-ci.org/yandex-money-tech/log4j-count-appender)
[![Build status](https://ci.appveyor.com/api/projects/status/2ee4wumomugjnnl7?svg=true)](https://ci.appveyor.com/project/f0y/log4j-count-appender)
[![codecov](https://codecov.io/gh/yandex-money-tech/log4j-count-appender/branch/master/graph/badge.svg)](https://codecov.io/gh/yandex-money-tech/log4j-count-appender)
[![Codebeat](https://codebeat.co/badges/ff7a4c21-72fb-446c-b245-ba739240fe49)](https://codebeat.co/projects/github-com-yandex-money-log4j-count-appender-master)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Javadoc](https://img.shields.io/badge/javadoc-latest-blue.svg)](https://yandex-money-tech.github.io/log4j-count-appender/)
[![Download](https://api.bintray.com/packages/yandex-money-tech/maven/log4j-count-appender/images/download.svg)](https://bintray.com/yandex-money-tech/maven/log4j-count-appender/_latestVersion)

# Appender для подсчета количества логов и отправки в сокет

SocketLogLevelCountAppender собирает статистику количества событий **LogEvent**, агрегируя по уровню **level**.
С заданной периодичностью происходит запись в сокет событий, содержащих:
- уровень логирования **level**,
- количество событий с момента последней записи в сокет.

**Важно!** Статистика количества событий не гарантирует 100% точность, следует ориентироваться на погрешность в 0.01% 
при нагрузке в 100000 событий за 100мс по одному level. Это обусловлено использованием ```LongAdder#sumThenReset()```.

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

# Подключение

Библиотека доступна в [Bintray's JCenter repository](http://jcenter.bintray.com) 

```
<dependency>
  <groupId>com.yandex.money.tech</groupId>
  <artifactId>log4j-count-appender</artifactId>
  <version>1.1.3</version>
</dependency>
```

# Сборка проекта

См. конфигурации Travis (`.travis.yml`) или AppVeyor (`appveyor.yml`).
В репозитории находятся два gradle-проекта:
- файлы `build.gradle`, `gradlew`, `gradle/wrapper` относятся к проекту для работы во внутренней инфраструктуре Яндекс.Денег;
- файлы `build-public.gradle`, `gradlew-public`, `gradle-public/wrapper` относятся к проекту для работы извне.

# Импорт проекта в IDE

К сожалению на данный момент необходимо перед импортом проекта в Idea заменить файлы:
- `gradle-public/wrapper/gradle-wrapper.properties` на `gradle/wrapper/gradle-wrapper.properties`,
- `build-public.gradle` with `build.gradle`.
Это вызвано багом в Idea: https://github.com/f0y/idea-two-gradle-builds.