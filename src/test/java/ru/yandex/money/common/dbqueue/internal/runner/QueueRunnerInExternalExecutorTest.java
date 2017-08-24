package ru.yandex.money.common.dbqueue.internal.runner;

import org.junit.Test;
import org.mockito.Matchers;
import ru.yandex.money.common.dbqueue.api.Queue;
import ru.yandex.money.common.dbqueue.api.TaskRecord;
import ru.yandex.money.common.dbqueue.settings.QueueConfig;
import ru.yandex.money.common.dbqueue.settings.QueueLocation;
import ru.yandex.money.common.dbqueue.settings.QueueSettings;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.Executor;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Oleg Kandaurov
 * @since 04.08.2017
 */
public class QueueRunnerInExternalExecutorTest {

    private static final QueueLocation testLocation1 =
            QueueLocation.builder().withTableName("queue_test").withQueueName("test_queue1").build();

    @Test
    public void should_wait_notasktimeout_when_no_task_found() throws Exception {
        Duration betweenTaskTimeout = Duration.ofHours(1L);
        Duration noTaskTimeout = Duration.ofMillis(5L);

        FakeExecutor executor = spy(new FakeExecutor());
        Queue queue = mock(Queue.class);
        TaskPicker taskPicker = mock(TaskPicker.class);
        when(taskPicker.pickTask(queue)).thenReturn(null);
        TaskProcessor taskProcessor = mock(TaskProcessor.class);

        when(queue.getQueueConfig()).thenReturn(new QueueConfig(testLocation1,
                QueueSettings.builder().withBetweenTaskTimeout(betweenTaskTimeout).withNoTaskTimeout(noTaskTimeout).build()));
        Duration waitTimeout = new QueueRunnerInExternalExecutor(taskPicker, taskProcessor, executor).runQueue(queue);

        assertThat(waitTimeout, equalTo(noTaskTimeout));

        verifyZeroInteractions(executor);
        verify(taskPicker).pickTask(queue);
        verifyZeroInteractions(taskProcessor);
    }

    @Test
    public void should_wait_betweentasktimeout_when_task_found() throws Exception {
        Duration betweenTaskTimeout = Duration.ofHours(1L);
        Duration noTaskTimeout = Duration.ofMillis(5L);

        FakeExecutor executor = spy(new FakeExecutor());
        Queue queue = mock(Queue.class);
        TaskPicker taskPicker = mock(TaskPicker.class);
        TaskRecord taskRecord = new TaskRecord(0, null, 0, ZonedDateTime.now(),
                ZonedDateTime.now(), null, null);
        when(taskPicker.pickTask(queue)).thenReturn(taskRecord);
        TaskProcessor taskProcessor = mock(TaskProcessor.class);


        when(queue.getQueueConfig()).thenReturn(new QueueConfig(testLocation1,
                QueueSettings.builder().withBetweenTaskTimeout(betweenTaskTimeout).withNoTaskTimeout(noTaskTimeout).build()));
        Duration waitTimeout = new QueueRunnerInExternalExecutor(taskPicker, taskProcessor, executor).runQueue(queue);

        assertThat(waitTimeout, equalTo(betweenTaskTimeout));

        verify(executor).execute(Matchers.any());
        verify(taskPicker).pickTask(queue);
        verify(taskProcessor).processTask(queue, taskRecord);
    }

    private static class FakeExecutor implements Executor {

        @Override
        public void execute(@Nonnull Runnable command) {
            command.run();
        }
    }

}