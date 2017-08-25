package ru.yandex.money.common.dbqueue.internal.runner;

import org.junit.Test;
import ru.yandex.money.common.dbqueue.api.TaskExecutionResult;
import ru.yandex.money.common.dbqueue.api.TaskRecord;
import ru.yandex.money.common.dbqueue.dao.QueueDao;
import ru.yandex.money.common.dbqueue.settings.QueueLocation;
import ru.yandex.money.common.dbqueue.stub.FakeTransactionTemplate;

import java.time.Duration;
import java.time.ZonedDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Oleg Kandaurov
 * @since 04.08.2017
 */
public class TaskResultHandlerTest {

    @Test
    public void should_reenqueue_task() throws Exception {
        long taskId = 5L;
        Duration reenqueueDelay = Duration.ofMillis(500L);
        QueueLocation location = QueueLocation.builder().withTableName("testTable").withQueueName("testQueue").build();

        TaskRecord taskRecord = new TaskRecord(taskId, null, 0, ZonedDateTime.now(),
                ZonedDateTime.now(), null, null);
        QueueDao queueDao = mock(QueueDao.class);
        when(queueDao.getTransactionTemplate()).thenReturn(new FakeTransactionTemplate());
        TaskExecutionResult result = TaskExecutionResult.reenqueue(reenqueueDelay);

        new TaskResultHandler(location, queueDao).handleResult(taskRecord, result);

        verify(queueDao).getTransactionTemplate();
        verify(queueDao).reenqueue(location, taskId, reenqueueDelay, true);
    }

    @Test
    public void should_finish_task() throws Exception {
        long taskId = 5L;
        QueueLocation location = QueueLocation.builder().withTableName("testTable").withQueueName("testQueue").build();

        TaskRecord taskRecord = new TaskRecord(taskId, null, 0, ZonedDateTime.now(),
                ZonedDateTime.now(), null, null);
        QueueDao queueDao = mock(QueueDao.class);
        when(queueDao.getTransactionTemplate()).thenReturn(new FakeTransactionTemplate());
        TaskExecutionResult result = TaskExecutionResult.finish();

        new TaskResultHandler(location, queueDao).handleResult(taskRecord, result);

        verify(queueDao).getTransactionTemplate();
        verify(queueDao).deleteTask(location, taskId);
    }

    @Test
    public void should_fail_task_when_delay_is_specified() throws Exception {
        long taskId = 5L;
        Duration executionDelay = Duration.ofMillis(500L);
        QueueLocation location = QueueLocation.builder().withTableName("testTable").withQueueName("testQueue").build();

        TaskRecord taskRecord = new TaskRecord(taskId, null, 0, ZonedDateTime.now(),
                ZonedDateTime.now(), null, null);
        QueueDao queueDao = mock(QueueDao.class);
        when(queueDao.getTransactionTemplate()).thenReturn(new FakeTransactionTemplate());
        TaskExecutionResult result = TaskExecutionResult.fail(executionDelay);

        new TaskResultHandler(location, queueDao).handleResult(taskRecord, result);

        verify(queueDao).getTransactionTemplate();
        verify(queueDao).reenqueue(location, taskId, executionDelay, false);
    }

    @Test
    public void should_fail_task_when_no_delay() throws Exception {
        QueueLocation location = QueueLocation.builder().withTableName("testTable").withQueueName("testQueue").build();

        TaskRecord taskRecord = new TaskRecord(0, null, 0, ZonedDateTime.now(),
                ZonedDateTime.now(), null, null);
        QueueDao queueDao = mock(QueueDao.class);
        when(queueDao.getTransactionTemplate()).thenReturn(new FakeTransactionTemplate());
        TaskExecutionResult result = TaskExecutionResult.fail();

        new TaskResultHandler(location, queueDao).handleResult(taskRecord, result);

        verifyZeroInteractions(queueDao);
    }
}