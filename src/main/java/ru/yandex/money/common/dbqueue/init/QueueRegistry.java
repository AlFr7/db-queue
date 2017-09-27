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
import ru.yandex.money.common.dbqueue.settings.QueueId;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private final Map<QueueId, QueueConsumer> consumers = new LinkedHashMap<>();
    private final Map<QueueId, TaskLifecycleListener> taskListeners = new LinkedHashMap<>();
    private final Map<QueueId, ThreadLifecycleListener> threadListeners = new LinkedHashMap<>();
    private final Map<QueueId, QueueExternalExecutor> externalExecutors = new LinkedHashMap<>();
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
        QueueId queueId = queueConsumer.getQueueConfig().getLocation().getQueueId();

        if (!Objects.equals(queueConsumer.getQueueConfig(), queueProducer.getQueueConfig())) {
            errorMessages.add(String.format("queue config must be the same: queueId=%s, producer=%s, " +
                    "consumer=%s", queueId, queueProducer.getQueueConfig(), queueConsumer.getQueueConfig()));
        }

        if (!Objects.equals(queueProducer.getPayloadTransformer(), queueConsumer.getPayloadTransformer())) {
            errorMessages.add(String.format("payload transformers must be the same: queueId=%s", queueId));
        }

        if (!Objects.equals(queueProducer.getShardRouter(), queueConsumer.getShardRouter())) {
            errorMessages.add(String.format("shard routers must be the same: queueId=%s", queueId));
        }

        if (consumers.putIfAbsent(queueId, queueConsumer) != null) {
            errorMessages.add("duplicate queue: queueId=" + queueId);
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
     * @param queueId              идентификатор очереди
     * @param taskLifecycleListener слушатель задач
     */
    public synchronized void registerTaskLifecycleListener(
            @Nonnull QueueId queueId, @Nonnull TaskLifecycleListener taskLifecycleListener) {
        Objects.requireNonNull(queueId);
        Objects.requireNonNull(taskLifecycleListener);
        ensureConstructionInProgress();
        if (taskListeners.putIfAbsent(queueId, taskLifecycleListener) != null) {
            errorMessages.add("duplicate task lifecycle listener: queueId=" + queueId);
        }
    }

    /**
     * Зарегистрировать слушатель потоков заданной очереди
     *
     * @param queueId                идентификатор очереди
     * @param threadLifecycleListener слушатель потоков
     */
    public synchronized void registerThreadLifecycleListener(
            @Nonnull QueueId queueId, @Nonnull ThreadLifecycleListener threadLifecycleListener) {
        Objects.requireNonNull(queueId);
        Objects.requireNonNull(threadLifecycleListener);
        ensureConstructionInProgress();
        if (threadListeners.putIfAbsent(queueId, threadLifecycleListener) != null) {
            errorMessages.add("duplicate thread lifecycle listener: queueId=" + queueId);
        }
    }

    /**
     * Зарегистрировать исполнителя задач для заданной очереди
     *
     * @param queueId         идентификатор очереди
     * @param externalExecutor исполнитель задач очереди
     */
    public synchronized void registerExternalExecutor(
            @Nonnull QueueId queueId, @Nonnull QueueExternalExecutor externalExecutor) {
        Objects.requireNonNull(queueId);
        Objects.requireNonNull(externalExecutor);
        ensureConstructionInProgress();
        if (externalExecutors.putIfAbsent(queueId, externalExecutor) != null) {
            errorMessages.add("duplicate external executor: queueId=" + queueId);
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
        if (!errorMessages.isEmpty()) {
            throw new IllegalArgumentException("Invalid queue configuration:" + System.lineSeparator() +
                    errorMessages.stream().collect(Collectors.joining(System.lineSeparator())));
        }
        consumers.values().forEach(consumer -> log.info("registered consumer: config={}", consumer.getQueueConfig()));
        shards.values().forEach(shard -> log.info("registered shard: shardId={}", shard.getShardId()));
        externalExecutors.keySet().forEach(queueId -> log.info("registered external executor: queueId={}", queueId));
        taskListeners.keySet().forEach(queueId ->
                log.info("registered task lifecycle listener: queueId={}", queueId));
        threadListeners.keySet().forEach(queueId ->
                log.info("registered thread lifecycle listener: queueId={}", queueId));

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
                    unusedShards.stream().map(QueueShardId::asString).collect(Collectors.joining(",")));
        }
    }

    private void validateTaskListeners() {
        for (QueueId queueId : taskListeners.keySet()) {
            if (!consumers.containsKey(queueId)) {
                errorMessages.add("no matching queue for task listener: queueId=" + queueId);
            }
        }
    }

    private void validateThreadListeners() {
        for (QueueId queueId : threadListeners.keySet()) {
            if (!consumers.containsKey(queueId)) {
                errorMessages.add("no matching queue for thread listener: queueId=" + queueId);
            }
        }
    }

    private void validateExternalExecutors() {
        for (QueueId queueId : externalExecutors.keySet()) {
            if (!consumers.containsKey(queueId)) {
                errorMessages.add("no matching queue for external executor: queueId=" + queueId);
            }
        }
        for (Map.Entry<QueueId, QueueConsumer> entry : consumers.entrySet()) {
            boolean isUseExternalExecutor = entry.getValue().getQueueConfig()
                    .getSettings().getProcessingMode() == ProcessingMode.USE_EXTERNAL_EXECUTOR;
            boolean hasExternalExecutor = externalExecutors.containsKey(entry.getKey());
            if (isUseExternalExecutor && !hasExternalExecutor) {
                errorMessages.add("external executor missing " +
                        "for processing mode " + ProcessingMode.USE_EXTERNAL_EXECUTOR + ": queueId=" + entry.getKey());
            }
            if (!isUseExternalExecutor && hasExternalExecutor) {
                errorMessages.add("external executor must be specified only " +
                        "for processing mode " + ProcessingMode.USE_EXTERNAL_EXECUTOR + ": queueId=" + entry.getKey());
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
     * @return Map: key - идентификатор очереди, value - слушатель задач данной очереди
     */
    @Nonnull
    Map<QueueId, TaskLifecycleListener> getTaskListeners() {
        ensureConstructionFinished();
        return Collections.unmodifiableMap(taskListeners);
    }

    /**
     * Получить зарегестрированные слушатели потоков
     *
     * @return Map: key - идентификатор очереди, value - слушатель потоков данной очереди
     */
    @Nonnull
    Map<QueueId, ThreadLifecycleListener> getThreadListeners() {
        ensureConstructionFinished();
        return Collections.unmodifiableMap(threadListeners);
    }

    /**
     * Получить исполнителей задач
     *
     * @return Map: key - идентификатор очереди, value - исполнитель данной очереди
     */
    @Nonnull
    Map<QueueId, QueueExternalExecutor> getExternalExecutors() {
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
