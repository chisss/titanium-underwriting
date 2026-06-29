package com.titanium.underwriting.infrastructure.config;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.titanium.underwriting.domain.aggregate.Underwriting;

/**
 * Axon Framework配置类
 */
@Configuration
public class AxonConfig {
    /**
     * 配置命令总线
     *
     * @return 命令总线实例
     */
    @Bean
    public CommandBus commandBus() {
        return SimpleCommandBus.builder().build();
    }

    /**
     * 配置核保聚合的事件溯源仓储
     *
     * @param eventStore 事件存储
     * @return 事件溯源仓储实例
     */
    @Bean
    public EventSourcingRepository<Underwriting> underwritingEventSourcingRepository(EventStore eventStore) {
        return EventSourcingRepository.builder(Underwriting.class).eventStore(eventStore).build();
    }
}
