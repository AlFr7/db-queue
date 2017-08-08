package ru.yandex.money.common.dbqueue.internal.runner;

import org.junit.Test;
import ru.yandex.money.common.dbqueue.dao.QueueDao;
import ru.yandex.money.common.dbqueue.api.Queue;
import ru.yandex.money.common.dbqueue.api.TaskRecord;
import ru.yandex.money.common.dbqueue.settings.QueueConfig;
import ru.yandex.money.common.dbqueue.settings.QueueLocation;
import ru.yandex.money.common.dbqueue.settings.QueueSettings;
import ru.yandex.money.common.dbqueue.stub.FakeTransactionTemplate;

import java.time.Duration;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Oleg Kandaurov
 * @since 04.08.2017
 */
public class QueueRunnerInTransactionTest {

    @Test
    public void should_wait_notasktimeout_when_no_task_found() throws Exception {
        Duration betweenTaskTimeout = Duration.ofHours(1L);
        Duration noTaskTimeout = Duration.ofMillis(5L);

        Queue queue = mock(Queue.class);
        TaskPicker taskPicker = mock(TaskPicker.class);
        when(taskPicker.pickTask(queue)).thenReturn(null);
        TaskProcessor taskProcessor = mock(TaskProcessor.class);
        QueueDao queueDao = mock(QueueDao.class);
        when(queueDao.getTransactionTemplate()).thenReturn(new FakeTransactionTemplate());

        when(queue.getQueueConfig()).thenReturn(new QueueConfig(mock(QueueLocation.class),
                QueueSettings.builder().withBetweenTaskTimeout(betweenTaskTimeout).withNoTaskTimeout(noTaskTimeout).build()));
        Duration waitTimeout = new QueueRunnerInTransaction(taskPicker, taskProcessor, queueDao).runQueue(queue);

        assertThat(waitTimeout, equalTo(noTaskTimeout));

        verify(queueDao).getTransactionTemplate();
        verify(taskPicker).pickTask(queue);
        verifyZeroInteractions(taskProcessor);
    }

    @Test
    public void should_wait_betweentasktimeout_when_task_found() throws Exception {
        Duration betweenTaskTimeout = Duration.ofHours(1L);
        Duration noTaskTimeout = Duration.ofMillis(5L);

        Queue queue = mock(Queue.class);
        TaskPicker taskPicker = mock(TaskPicker.class);
        TaskRecord taskRecord = mock(TaskRecord.class);
        when(taskPicker.pickTask(queue)).thenReturn(taskRecord);
        TaskProcessor taskProcessor = mock(TaskProcessor.class);
        QueueDao queueDao = mock(QueueDao.class);
        when(queueDao.getTransactionTemplate()).thenReturn(new FakeTransactionTemplate());


        when(queue.getQueueConfig()).thenReturn(new QueueConfig(mock(QueueLocation.class),
                QueueSettings.builder().withBetweenTaskTimeout(betweenTaskTimeout).withNoTaskTimeout(noTaskTimeout).build()));
        Duration waitTimeout = new QueueRunnerInTransaction(taskPicker, taskProcessor, queueDao).runQueue(queue);

        assertThat(waitTimeout, equalTo(betweenTaskTimeout));

        verify(queueDao).getTransactionTemplate();
        verify(taskPicker).pickTask(queue);
        verify(taskProcessor).processTask(queue, taskRecord);
    }
}