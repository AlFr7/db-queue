package ru.yandex.money.common.dbqueue.internal.runner;

import ru.yandex.money.common.dbqueue.api.Queue;
import ru.yandex.money.common.dbqueue.api.QueueAction;
import ru.yandex.money.common.dbqueue.api.Task;
import ru.yandex.money.common.dbqueue.api.TaskLifecycleListener;
import ru.yandex.money.common.dbqueue.api.TaskRecord;
import ru.yandex.money.common.dbqueue.dao.QueueDao;
import ru.yandex.money.common.dbqueue.internal.MillisTimeProvider;

import javax.annotation.Nonnull;

import static java.util.Objects.requireNonNull;

/**
 * Обработчик выбранной задачи
 *
 * @author Oleg Kandaurov
 * @since 19.07.2017
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class TaskProcessor {

    @Nonnull
    private final QueueDao queueDao;
    @Nonnull
    private final TaskLifecycleListener taskLifecycleListener;
    @Nonnull
    private final MillisTimeProvider millisTimeProvider;
    @Nonnull
    private final TaskResultHandler taskResultHandler;

    /**
     * Конструктор
     *
     * @param queueDao              шард на котором происходит выполнение задачи
     * @param taskLifecycleListener слушатель жизненного цикла задачи в очереди
     * @param millisTimeProvider    поставщик текущего времени
     * @param taskResultHandler     обработчик результата выполнения задачи
     */
    TaskProcessor(@Nonnull QueueDao queueDao,
                  @Nonnull TaskLifecycleListener taskLifecycleListener,
                  @Nonnull MillisTimeProvider millisTimeProvider,
                  @Nonnull TaskResultHandler taskResultHandler) {
        this.queueDao = requireNonNull(queueDao);
        this.taskLifecycleListener = requireNonNull(taskLifecycleListener);
        this.millisTimeProvider = requireNonNull(millisTimeProvider);
        this.taskResultHandler = requireNonNull(taskResultHandler);
    }

    /**
     * Передать выбранную задачу в клиентский код на выполнение и обработать результат
     *
     * @param queue      очередь
     * @param taskRecord запись на обработку
     */
    void processTask(@Nonnull Queue queue, @Nonnull TaskRecord taskRecord) {
        requireNonNull(queue);
        requireNonNull(taskRecord);
        try {
            taskLifecycleListener.started(queueDao.getShardId(), queue.getQueueConfig().getLocation(), taskRecord);
            long processTaskStarted = millisTimeProvider.getMillis();
            Object payload = queue.getPayloadTransformer().toObject(taskRecord.getPayload());
            Task task = new Task(queueDao.getShardId(), payload, taskRecord.getAttemptsCount(),
                    taskRecord.getCreateDate(), taskRecord.getCorrelationId(), taskRecord.getActor());
            QueueAction executionResult = queue.execute(task);
            taskLifecycleListener.executed(queueDao.getShardId(), queue.getQueueConfig().getLocation(), taskRecord,
                    executionResult, millisTimeProvider.getMillis() - processTaskStarted);
            taskResultHandler.handleResult(taskRecord, executionResult);
        } catch (Exception exc) {
            taskLifecycleListener.crashed(queueDao.getShardId(), queue.getQueueConfig().getLocation(), taskRecord, exc);
        } finally {
            taskLifecycleListener.finished(queueDao.getShardId(), queue.getQueueConfig().getLocation(), taskRecord);
        }
    }

}
