package ru.yandex.money.common.dbqueue.spring;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import ru.yandex.money.common.dbqueue.api.QueueShardId;
import ru.yandex.money.common.dbqueue.dao.QueueDao;
import ru.yandex.money.common.dbqueue.settings.QueueLocation;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Сборщик бинов, необходимых для конфигурирования очереди
 *
 * @author Oleg Kandaurov
 * @since 31.07.2017
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class SpringQueueCollector implements BeanPostProcessor, ApplicationListener<ContextRefreshedEvent> {
    private final Map<QueueLocation, SpringQueueProducer> producers = new LinkedHashMap<>();
    private final Map<QueueLocation, SpringQueueConsumer> queueConsumers = new LinkedHashMap<>();
    private final Map<QueueLocation, SpringTaskLifecycleListener> taskListeners = new LinkedHashMap<>();
    private final Map<QueueLocation, SpringThreadLifecycleListener> threadListeners = new LinkedHashMap<>();
    private final Map<QueueLocation, SpringQueueExternalExecutor> executors = new LinkedHashMap<>();
    private final Map<QueueLocation, SpringTaskPayloadTransformer> transformers = new LinkedHashMap<>();
    private final Map<QueueLocation, SpringQueueShardRouter> shardRouters = new LinkedHashMap<>();
    private final Map<QueueShardId, QueueDao> shards = new LinkedHashMap<>();
    private final Collection<String> errorMessages = new ArrayList<>();

    private <T extends SpringQueueIdentifiable> void collectBeanIfPossible(
            Class<T> clazz, Object bean, Map<QueueLocation, T> storage, String beanName) {
        if (clazz.isAssignableFrom(bean.getClass())) {
            T obj = clazz.cast(bean);
            if (storage.containsKey(obj.getQueueLocation())) {
                errorMessages.add(String.format("duplicate bean: name=%s, class=%s, location=%s",
                        beanName, clazz.getSimpleName(), obj.getQueueLocation()));
                return;
            }
            storage.put(obj.getQueueLocation(), obj);
        }
    }

    private void collectShardIfPossible(Object bean, Map<QueueShardId, QueueDao> storage, String beanName) {
        if (QueueDao.class.isAssignableFrom(bean.getClass())) {
            QueueDao shard = QueueDao.class.cast(bean);
            if (storage.containsKey(shard.getShardId())) {
                errorMessages.add(String.format("duplicate bean: name=%s, class=%s, shardId=%s",
                        beanName, QueueDao.class.getSimpleName(), shard.getShardId()));
                return;
            }
            storage.put(shard.getShardId(), shard);
        }
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (!errorMessages.isEmpty()) {
            throw new IllegalArgumentException("unable to collect queue beans:" + System.lineSeparator() +
                    errorMessages.stream().collect(Collectors.joining(System.lineSeparator())));
        }
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        collectBeanIfPossible(SpringQueueConsumer.class, bean, queueConsumers, beanName);
        collectBeanIfPossible(SpringQueueProducer.class, bean, producers, beanName);
        collectBeanIfPossible(SpringTaskLifecycleListener.class, bean, taskListeners, beanName);
        collectBeanIfPossible(SpringThreadLifecycleListener.class, bean, threadListeners, beanName);
        collectBeanIfPossible(SpringQueueExternalExecutor.class, bean, executors, beanName);
        collectBeanIfPossible(SpringTaskPayloadTransformer.class, bean, transformers, beanName);
        collectBeanIfPossible(SpringQueueShardRouter.class, bean, shardRouters, beanName);
        collectShardIfPossible(bean, shards, beanName);
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    /**
     * Получить постановщики задач, найденные в spring контексте.
     *
     * @return Map: key - местоположение очереди, value - постановщик задач данной очереди
     */
    @Nonnull
    Map<QueueLocation, SpringQueueProducer> getProducers() {
        return Collections.unmodifiableMap(producers);
    }

    /**
     * Получить обработчиков очереди, найденные в spring контексте.
     *
     * @return Map: key - местоположение очереди, value - обработчик очереди
     */
    @Nonnull
    Map<QueueLocation, SpringQueueConsumer> getConsumers() {
        return Collections.unmodifiableMap(queueConsumers);
    }

    /**
     * Получить слушателей задач в данной очереди, найденных в spring контексте.
     *
     * @return Map: key - местоположение очереди, value - слушатель задач данной очереди
     */
    @Nonnull
    Map<QueueLocation, SpringTaskLifecycleListener> getTaskListeners() {
        return Collections.unmodifiableMap(taskListeners);
    }

    /**
     * Получить слушателей потоков в данной очереди, найденных в spring контексте.
     *
     * @return Map: key - местоположение очереди, value - слушатель потоков данной очереди
     */
    @Nonnull
    Map<QueueLocation, SpringThreadLifecycleListener> getThreadListeners() {
        return Collections.unmodifiableMap(threadListeners);
    }

    /**
     * Получить исполнителей задач, найденных в spring контексте.
     *
     * @return Map: key - местоположение очереди, value - исполнитель задач данной очереди
     */
    @Nonnull
    Map<QueueLocation, SpringQueueExternalExecutor> getExecutors() {
        return Collections.unmodifiableMap(executors);
    }

    /**
     * Получить преобразователи данных задачи, найденные в spring контексте.
     *
     * @return Map: key - местоположение очереди, value - преобразователь данных задачи для данной очереди
     */
    @Nonnull
    Map<QueueLocation, SpringTaskPayloadTransformer> getTransformers() {
        return Collections.unmodifiableMap(transformers);
    }

    /**
     * Получить правила шардирования, найденные в spring контексте.
     *
     * @return Map: key - местоположение очереди, value - правила шардирования задач в очереди
     */
    @Nonnull
    Map<QueueLocation, SpringQueueShardRouter> getShardRouters() {
        return Collections.unmodifiableMap(shardRouters);
    }

    /**
     * Получить шарды, найденные в spring контексте.
     *
     * @return Map: key - идентификатор шарды, value - dao для работы с данным шардом
     */
    @Nonnull
    Map<QueueShardId, QueueDao> getShards() {
        return Collections.unmodifiableMap(shards);
    }

}
