package ru.yandex.money.common.dbqueue.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Параметры типизированной задачи, поставляемые в обработчик очереди.
 *
 * @param <T> тип данных задачи
 * @author Oleg Kandaurov
 * @since 10.07.2017
 */
public final class Task<T> {

    @Nonnull
    private final QueueShardId shardId;
    @Nullable
    private final T payload;
    private final long attemptsCount;
    @Nonnull
    private final ZonedDateTime createDate;
    @Nullable
    private final String correlationId;
    @Nullable
    private final String actor;

    /**
     * Конструктор типизированных параметров задачи
     *
     * @param shardId       идентификатор шарда, с которого была взята задача
     * @param payload       данные задачи
     * @param attemptsCount количество попыток выполнения, включая текущую
     * @param createDate    дата помещения задачи в очередь
     * @param correlationId технический идентификатор задачи в очереди
     * @param actor         бизнесовый идентификатор задачи в очереди
     */
    public Task(@Nonnull QueueShardId shardId, @Nullable T payload, long attemptsCount,
                @Nonnull ZonedDateTime createDate, @Nullable String correlationId, @Nullable String actor) {
        this.shardId = Objects.requireNonNull(shardId);
        this.payload = payload;
        this.attemptsCount = attemptsCount;
        this.createDate = Objects.requireNonNull(createDate);
        this.correlationId = correlationId;
        this.actor = actor;
    }

    /**
     * Получить типизированные данные задачи
     *
     * @return типизированные данные задачи
     */
    @Nonnull
    public Optional<T> getPayload() {
        return Optional.ofNullable(payload);
    }

    /**
     * Получить типизированные данные задачи или выбросить исключение
     * при их отсутствии.
     *
     * @return типизированные данные задачи
     */
    @Nonnull
    public T getPayloadOrThrow() {
        if (payload == null) {
            throw new IllegalArgumentException("payload is absent");
        }
        return payload;
    }

    /**
     * Получить количество попыток исполнения задачи, включая текущую.
     *
     * @return количество попыток исполнения
     */
    public long getAttemptsCount() {
        return attemptsCount;
    }

    /**
     * Получить дату постановки задачи в очередь
     *
     * @return дата постановки задачи
     */
    @Nonnull
    public ZonedDateTime getCreateDate() {
        return createDate;
    }

    /**
     * Получить технический идентификатор задачи.
     *
     * @return технический идентификатор
     * @throws IllegalArgumentException если идентификатор отсутствует
     */
    @Nonnull
    public String getCorrelationIdOrThrow() {
        if (correlationId == null) {
            throw new IllegalArgumentException("correlationId is absent");
        }
        return correlationId;
    }

    /**
     * Получить технический идентификатор задачи
     *
     * @return технический идентификатор
     */
    @Nonnull
    public Optional<String> getCorrelationId() {
        return Optional.ofNullable(correlationId);
    }

    /**
     * Получить бизнесовый идентификатор задачи.
     *
     * @return бизнесовый идентификатор задачи
     * @throws IllegalArgumentException если идентификатор отсутствует
     */
    @Nonnull
    public String getActorOrThrow() {
        if (actor == null) {
            throw new IllegalArgumentException("actor is absent");
        }
        return actor;
    }

    /**
     * Получить бизнесовый идентификатор задачи.
     *
     * @return идентификатор задачи.
     */
    @Nonnull
    public Optional<String> getActor() {
        return Optional.ofNullable(actor);
    }

    /**
     * Получить идентификатор шарда, с которого была взята задача
     *
     * @return идентификатор шарда
     */
    @Nonnull
    public QueueShardId getShardId() {
        return shardId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Task<?> task = (Task<?>) obj;
        return attemptsCount == task.attemptsCount &&
                Objects.equals(shardId, task.shardId) &&
                Objects.equals(payload, task.payload) &&
                Objects.equals(createDate, task.createDate) &&
                Objects.equals(correlationId, task.correlationId) &&
                Objects.equals(actor, task.actor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shardId, payload, attemptsCount, createDate, correlationId, actor);
    }

    @Override
    public String toString() {
        return '{' +
                "shardId=" + shardId +
                ", attemptsCount=" + attemptsCount +
                ", createDate=" + createDate +
                ", payload=" + payload +
                (correlationId != null ? ", correlationId=" + correlationId : "") +
                (actor != null ? ", actor=" + actor : "") +
                '}';
    }
}
