package ru.yandex.money.common.dbqueue.internal.runner;

import org.junit.Assert;
import org.junit.Test;
import ru.yandex.money.common.dbqueue.api.QueueShardId;
import ru.yandex.money.common.dbqueue.dao.QueueDao;
import ru.yandex.money.common.dbqueue.api.EnqueueParams;
import ru.yandex.money.common.dbqueue.api.TaskRecord;
import ru.yandex.money.common.dbqueue.dao.BaseDaoTest;
import ru.yandex.money.common.dbqueue.settings.QueueLocation;
import ru.yandex.money.common.dbqueue.settings.QueueSettings;

import java.math.BigInteger;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Objects;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;

/**
 * @author Oleg Kandaurov
 * @since 15.07.2017
 */
public class PickTaskDaoTest extends BaseDaoTest {

    private final QueueDao queueDao = new QueueDao(new QueueShardId("s1"), jdbcTemplate, transactionTemplate);
    private final PickTaskDao pickTaskDao = new PickTaskDao(new QueueShardId("s1"), jdbcTemplate, transactionTemplate);

    /**
     * Из-за особенностей windows какая-то фигня со временем БД
     */
    private final static Duration WINDOWS_OS_DELAY = Duration.ofSeconds(1);

    @Test
    public void should_not_pick_task_too_early() throws Exception {
        QueueLocation location = generateUniqueLocation();
        executeInTransaction(() ->
                queueDao.enqueue(location, new EnqueueParams<String>().withExecutionDelay(Duration.ofHours(1))));
        TaskRecord taskRecord = pickTaskDao.pickTask(location, new RetryTaskStrategy.ArithmeticBackoff());
        Assert.assertThat(taskRecord, is(nullValue()));
    }

    @Test
    public void pick_task_should_return_all_fields() throws Exception {
        QueueLocation location = generateUniqueLocation();
        String payload = "{}";
        String correlationId = "#11";
        String actor = "id-123";
        ZonedDateTime beforeEnqueue = ZonedDateTime.now();
        long enqueueId = executeInTransaction(() -> queueDao.enqueue(location,
                EnqueueParams.create(payload).withCorrelationId(correlationId).withActor(actor)));
        TaskRecord taskRecord = executeInTransaction(
                () -> pickTaskDao.pickTask(location, new RetryTaskStrategy.ArithmeticBackoff()));
        ZonedDateTime afterEnqueue = ZonedDateTime.now();
        Assert.assertThat(taskRecord, is(not(nullValue())));
        Objects.requireNonNull(taskRecord);
        Assert.assertThat(taskRecord.getActor(), equalTo(actor));
        Assert.assertThat(taskRecord.getAttemptsCount(), equalTo(1L));
        Assert.assertThat(taskRecord.getCorrelationId(), equalTo(correlationId));
        Assert.assertThat(taskRecord.getId(), equalTo(enqueueId));
        Assert.assertThat(taskRecord.getPayload(), equalTo(payload));
        Assert.assertThat(taskRecord.getProcessTime(), is(not(nullValue())));
        Assert.assertThat(taskRecord.getCreateDate().isAfter(beforeEnqueue), equalTo(true));
        Assert.assertThat(taskRecord.getCreateDate().isBefore(afterEnqueue), equalTo(true));
    }

    @Test
    public void pick_task_should_delay_with_fixed_interval_strategy() {
        QueueLocation location = generateUniqueLocation();
        Duration expectedDelay = Duration.ofMinutes(3L);
        ZonedDateTime beforePickingTask;
        ZonedDateTime afterPickingTask;
        TaskRecord taskRecord;
        RetryTaskStrategy retryTaskStrategy = new RetryTaskStrategy.FixedInterval(
                QueueSettings.builder().withNoTaskTimeout(Duration.ZERO)
                        .withBetweenTaskTimeout(Duration.ZERO)
                        .putSetting(QueueSettings.AdditionalSetting.RETRY_FIXED_INTERVAL_DELAY, "PT3M")
                        .build());


        Long enqueueId = executeInTransaction(() -> queueDao.enqueue(location, new EnqueueParams<>()));

        for (int attempt = 1; attempt < 10; attempt++) {
            beforePickingTask = ZonedDateTime.now();
            taskRecord = resetProcessTimeAndPick(location, retryTaskStrategy, enqueueId);
            afterPickingTask = ZonedDateTime.now();
            Assert.assertThat(taskRecord.getAttemptsCount(), equalTo((long) attempt));
            Assert.assertThat(taskRecord.getProcessTime().isAfter(beforePickingTask.plus(expectedDelay)), equalTo(true));
            Assert.assertThat(taskRecord.getProcessTime().isBefore(afterPickingTask.plus(expectedDelay).plus(WINDOWS_OS_DELAY)), equalTo(true));
        }
    }

    @Test
    public void pick_task_should_delay_with_arithmetic_strategy() {
        QueueLocation location = generateUniqueLocation();
        Duration expectedDelay;
        ZonedDateTime beforePickingTask;
        ZonedDateTime afterPickingTask;
        TaskRecord taskRecord;
        RetryTaskStrategy retryTaskStrategy = new RetryTaskStrategy.ArithmeticBackoff();


        Long enqueueId = executeInTransaction(() -> queueDao.enqueue(location, new EnqueueParams<>()));

        for (int attempt = 1; attempt < 10; attempt++) {
            beforePickingTask = ZonedDateTime.now();
            taskRecord = resetProcessTimeAndPick(location, retryTaskStrategy, enqueueId);
            afterPickingTask = ZonedDateTime.now();
            expectedDelay = Duration.ofMinutes((long) (1 + (attempt - 1) * 2));
            Assert.assertThat(taskRecord.getAttemptsCount(), equalTo((long) attempt));
            Assert.assertThat(taskRecord.getProcessTime().isAfter(beforePickingTask.plus(expectedDelay)), equalTo(true));
            Assert.assertThat(taskRecord.getProcessTime().isBefore(afterPickingTask.plus(expectedDelay.plus(WINDOWS_OS_DELAY))), equalTo(true));
        }
    }

    @Test
    public void pick_task_should_delay_with_geometric_strategy() {
        QueueLocation location = generateUniqueLocation();
        Duration expectedDelay;
        ZonedDateTime beforePickingTask;
        ZonedDateTime afterPickingTask;
        TaskRecord taskRecord;

        RetryTaskStrategy retryTaskStrategy = new RetryTaskStrategy.GeometricBackoff();

        Long enqueueId = executeInTransaction(() -> queueDao.enqueue(location, new EnqueueParams<>()));

        for (int attempt = 1; attempt < 10; attempt++) {
            beforePickingTask = ZonedDateTime.now();
            taskRecord = resetProcessTimeAndPick(location, retryTaskStrategy, enqueueId);
            afterPickingTask = ZonedDateTime.now();
            expectedDelay = Duration.ofMinutes(BigInteger.valueOf(2L).pow(attempt - 1).longValue());
            Assert.assertThat(taskRecord.getAttemptsCount(), equalTo((long) attempt));
            Assert.assertThat(taskRecord.getProcessTime().isAfter(beforePickingTask.plus(expectedDelay)), equalTo(true));
            Assert.assertThat(taskRecord.getProcessTime().isBefore(afterPickingTask.plus(expectedDelay.plus(WINDOWS_OS_DELAY))), equalTo(true));
        }
    }

    private TaskRecord resetProcessTimeAndPick(QueueLocation location, RetryTaskStrategy retryTaskStrategy, Long enqueueId) {
        executeInTransaction(() -> {
            jdbcTemplate.update("update " + TABLE_NAME + " set process_time=now() where id=" + enqueueId);
        });
        return executeInTransaction(
                () -> pickTaskDao.pickTask(location, retryTaskStrategy));
    }
}
