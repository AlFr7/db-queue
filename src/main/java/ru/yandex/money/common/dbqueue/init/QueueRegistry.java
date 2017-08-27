package ru.yandex.money.common.dbqueue.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.yandex.money.common.dbqueue.api.QueueConsumer;
import ru.yandex.money.common.dbqueue.api.QueueExternalExecutor;
import ru.yandex.money.common.dbqueue.api.QueueProducer;
import ru.yandex.money.common.dbqueue.api.QueueShardId;
import ru.yandex.money.common.dbqueue.api.QueueShardRouter;
import ru.yandex.money.common.dbqueue.api.TaskLifecycleListener;
import ru.yandex.money.common.dbqueue.api.ThreadLifecycleListener;
import ru.yandex.money.common.dbqueue.dao.QueueDao;
import ru.yandex.money.common.dbqueue.settings.ProcessingMode;
import ru.yandex.money.common.dbqueue.settings.QueueLocation;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Хранилище очередей.
 * <p>
 * Хранит не только очереди, а также объекты, относящиеся к данной очереди.
 * Данный класс предоставляет единое место контроля за корректностью конфигурации очередей.
 *
 * @author Oleg Kandaurov
 * @since 09.07.2017
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class QueueRegistry {

    private static final Logger log = LoggerFactory.getLogger(QueueRegistry.class);

    private final Map<QueueLocation, QueueConsumer> consumers = new LinkedHashMap<>();
    private final Map<QueueLocation, TaskLifecycleListener> taskListeners = new LinkedHashMap<>();
    private final Map<QueueLocation, ThreadLifecycleListener> threadListeners = new LinkedHashMap<>();
    private final Map<QueueLocation, QueueExternalExecutor> externalExecutors = new LinkedHashMap<>();
    private final Map<QueueShardId, QueueDao> shards = new LinkedHashMap<>();
    private final Collection<String> errorMessages = new ArrayList<>();

    private volatile boolean isRegistrationFinished;

    /**
     * Зарегистрировать очередь.
     *
     * @param <T>           тип данных задачи
     * @param queueConsumer обработчик очереди
     * @param queueProducer постановщик задачи в очередь
     */
    public synchronized <T> void registerQueue(@Nonnull QueueConsumer<T> queueConsumer,
                                               @Nonnull QueueProducer<T> queueProducer) {
        Objects.requireNonNull(queueConsumer);
        Objects.requireNonNull(queueProducer);
        ensureConstructionInProgress();
        QueueLocation location = queueConsumer.getQueueConfig().getLocation();

        if (!Objects.equals(queueConsumer.getQueueConfig(), queueProducer.getQueueConfig())) {
            errorMessages.add(String.format("queue config must be the same: location=%s, producer=%s, " +
                    "consumer=%s", location, queueProducer.getQueueConfig(), queueConsumer.getQueueConfig()));
        }

        if (!Objects.equals(queueProducer.getPayloadTransformer(), queueConsumer.getPayloadTransformer())) {
            errorMessages.add(String.format("payload transformers must be the same: location=%s", location));
        }

        if (!Objects.equals(queueProducer.getShardRouter(), queueConsumer.getShardRouter())) {
            errorMessages.add(String.format("shard routers must be the same: location=%s", location));
        }

        if (consumers.putIfAbsent(location, queueConsumer) != null) {
            errorMessages.add("duplicate queue: location=" + location);
        }
    }

    /**
     * Зарегистрировать шард БД
     *
     * @param queueDao dao для работы с шардом
     */
    public synchronized void registerShard(@Nonnull QueueDao queueDao) {
        Objects.requireNonNull(queueDao);
        ensureConstructionInProgress();
        QueueShardId shardId = queueDao.getShardId();
        if (shards.putIfAbsent(shardId, queueDao) != null) {
            errorMessages.add("duplicate shard: shardId=" + shardId);
        }
    }

    /**
     * Зарегистрировать слушатель задач заданной очереди
     *
     * @param location              идентификатор очереди
     * @param taskLifecycleListener слушатель задач
     */
    public synchronized void registerTaskLifecycleListener(
            @Nonnull QueueLocation location, @Nonnull TaskLifecycleListener taskLifecycleListener) {
        Objects.requireNonNull(location);
        Objects.requireNonNull(taskLifecycleListener);
        ensureConstructionInProgress();
        if (taskListeners.putIfAbsent(location, taskLifecycleListener) != null) {
            errorMessages.add("duplicate task lifecycle listener: location=" + location);
        }
    }

    /**
     * Зарегистрировать слушатель потоков заданной очереди
     *
     * @param location                идентификатор очереди
     * @param threadLifecycleListener слушатель потоков
     */
    public synchronized void registerThreadLifecycleListener(
            @Nonnull QueueLocation location, @Nonnull ThreadLifecycleListener threadLifecycleListener) {
        Objects.requireNonNull(location);
        Objects.requireNonNull(threadLifecycleListener);
        ensureConstructionInProgress();
        if (threadListeners.putIfAbsent(location, threadLifecycleListener) != null) {
            errorMessages.add("duplicate thread lifecycle listener: location=" + location);
        }
    }

    /**
     * Зарегистрировать исполнителя задач для заданной очереди
     *
     * @param location         идентификатор очереди
     * @param externalExecutor исполнитель задач очереди
     */
    public synchronized void registerExternalExecutor(
            @Nonnull QueueLocation location, @Nonnull QueueExternalExecutor externalExecutor) {
        Objects.requireNonNull(location);
        Objects.requireNonNull(externalExecutor);
        ensureConstructionInProgress();
        if (externalExecutors.putIfAbsent(location, externalExecutor) != null) {
            errorMessages.add("duplicate external executor: location=" + location);
        }
    }

    /**
     * Завершить регистрацию конфигурации очередей
     */
    public synchronized void finishRegistration() {
        isRegistrationFinished = true;
        validateShards();
        validateTaskListeners();
        validateThreadListeners();
        validateExternalExecutors();
        validateQueueNames();
        if (!errorMessages.isEmpty()) {
            throw new IllegalArgumentException("Invalid queue configuration:" + System.lineSeparator() +
                    errorMessages.stream().collect(Collectors.joining(System.lineSeparator())));
        }
        consumers.values().forEach(consumer -> log.info("registered consumer: config={}", consumer.getQueueConfig()));
        shards.values().forEach(shard -> log.info("registered shard: shardId={}", shard.getShardId()));
        externalExecutors.keySet().forEach(location -> log.info("registered external executor: location={}", location));
        taskListeners.keySet().forEach(location ->
                log.info("registered task lifecycle listener: location={}", location));
        threadListeners.keySet().forEach(location ->
                log.info("registered thread lifecycle listener: location={}", location));

    }

    private void validateQueueNames() {
        Map<String, Set<String>> queueTablesMap = new HashMap<>();
        for (QueueLocation queueLocation : consumers.keySet()) {
            if (!queueTablesMap.containsKey(queueLocation.getQueueName())) {
                queueTablesMap.put(queueLocation.getQueueName(), new LinkedHashSet<>());
            }
            queueTablesMap.get(queueLocation.getQueueName()).add(queueLocation.getTableName());
        }
        queueTablesMap.forEach((queueName, tableNames) -> {
            if (tableNames.size() > 1) {
                errorMessages.add(String.format("queue name must be unique across all tables: " +
                        "queueName=%s, tables=%s", queueName, tableNames));
            }
        });
    }

    private void validateShards() {
        Map<QueueShardId, Boolean> shardsInUse = shards.keySet().stream()
                .collect(Collectors.toMap(id -> id, id -> Boolean.FALSE));
        for (QueueShardRouter shardRouter : consumers.values().stream()
                .map(QueueConsumer::getShardRouter).collect(Collectors.toList())) {
            Collection<QueueShardId> routerShards = shardRouter.getShardsId();
            for (QueueShardId shardId : routerShards) {
                if (shards.containsKey(shardId)) {
                    shardsInUse.put(shardId, Boolean.TRUE);
                } else {
                    errorMessages.add("shard not found: shardId=" + shardId);
                }
            }
        }
        List<QueueShardId> unusedShards = shardsInUse.entrySet().stream()
                .filter(inUse -> !inUse.getValue()).map(Map.Entry::getKey).collect(Collectors.toList());
        if (!unusedShards.isEmpty()) {
            errorMessages.add("shards is not used: shardIds=" +
                    unusedShards.stream().map(QueueShardId::getId).collect(Collectors.joining(",")));
        }
    }

    private void validateTaskListeners() {
        for (QueueLocation location : taskListeners.keySet()) {
            if (!consumers.containsKey(location)) {
                errorMessages.add("no matching queue for task listener: location=" + location);
            }
        }
    }

    private void validateThreadListeners() {
        for (QueueLocation location : threadListeners.keySet()) {
            if (!consumers.containsKey(location)) {
                errorMessages.add("no matching queue for thread listener: location=" + location);
            }
        }
    }

    private void validateExternalExecutors() {
        for (QueueLocation location : externalExecutors.keySet()) {
            if (!consumers.containsKey(location)) {
                errorMessages.add("no matching queue for external executor: location=" + location);
            }
        }
        for (Map.Entry<QueueLocation, QueueConsumer> entry : consumers.entrySet()) {
            boolean isUseExternalExecutor = entry.getValue().getQueueConfig()
                    .getSettings().getProcessingMode() == ProcessingMode.USE_EXTERNAL_EXECUTOR;
            boolean hasExternalExecutor = externalExecutors.containsKey(entry.getKey());
            if (isUseExternalExecutor && !hasExternalExecutor) {
                errorMessages.add("external executor missing " +
                        "for processing mode " + ProcessingMode.USE_EXTERNAL_EXECUTOR + ": location=" + entry.getKey());
            }
            if (!isUseExternalExecutor && hasExternalExecutor) {
                errorMessages.add("external executor must be specified only " +
                        "for processing mode " + ProcessingMode.USE_EXTERNAL_EXECUTOR + ": location=" + entry.getKey());
            }
        }

    }

    /**
     * Получить список зарегистрированных обработчиков очередей
     *
     * @return список очередей
     */
    @Nonnull
    Collection<QueueConsumer> getConsumers() {
        ensureConstructionFinished();
        return Collections.unmodifiableCollection(consumers.values());
    }

    /**
     * Получить зарегестрированные слушатели задач
     *
     * @return Map: key - местоположение очереди, value - слушатель задач данной очереди
     */
    @Nonnull
    Map<QueueLocation, TaskLifecycleListener> getTaskListeners() {
        ensureConstructionFinished();
        return Collections.unmodifiableMap(taskListeners);
    }

    /**
     * Получить зарегестрированные слушатели потоков
     *
     * @return Map: key - местоположение очереди, value - слушатель потоков данной очереди
     */
    @Nonnull
    Map<QueueLocation, ThreadLifecycleListener> getThreadListeners() {
        ensureConstructionFinished();
        return Collections.unmodifiableMap(threadListeners);
    }

    /**
     * Получить исполнителей задач
     *
     * @return Map: key - местоположение очереди, value - исполнитель данной очереди
     */
    @Nonnull
    Map<QueueLocation, QueueExternalExecutor> getExternalExecutors() {
        ensureConstructionFinished();
        return Collections.unmodifiableMap(externalExecutors);
    }

    /**
     * Получить зарегестрированные шарды
     *
     * @return Map: key - идентификатор шарда, value - dao для работы с данным шардом
     */
    @Nonnull
    Map<QueueShardId, QueueDao> getShards() {
        ensureConstructionFinished();
        return Collections.unmodifiableMap(shards);
    }

    private void ensureConstructionFinished() {
        if (!isRegistrationFinished) {
            throw new IllegalStateException("cannot get registry property. construction is not finished");
        }
    }

    private void ensureConstructionInProgress() {
        if (isRegistrationFinished) {
            throw new IllegalStateException("cannot update property. construction is finished");
        }
    }


}
