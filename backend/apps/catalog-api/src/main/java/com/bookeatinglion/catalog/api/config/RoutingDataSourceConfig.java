package com.bookeatinglion.catalog.api.config;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 쓰기는 writer(RDS Proxy 경유), 읽기는 reader(리드 리플리카)로 커넥션을 나눈다.
 * 판단 기준은 {@code @Transactional(readOnly = true)} — BookService 등 조회
 * 메서드 대부분에 이미 붙어 있어 새로 손댈 곳이 거의 없다.
 *
 * <p>{@link LazyConnectionDataSourceProxy}가 없으면 Spring이 트랜잭션 시작
 * "전"에 커넥션을 먼저 잡아버려서 readOnly 판단 시점이 지나버린다 - 그러면
 * 에러 없이 조용히 전부 writer로만 간다(라우팅이 무효화된다).
 *
 * <p>Liquibase는 트랜잭션 밖에서 자기 커넥션을 관리해서
 * {@code isCurrentTransactionReadOnly()}가 항상 false다 - 그래서 마이그레이션은
 * 자동으로 writer로 간다.
 *
 * <p>prod 프로파일에서만 켠다 - k8s dev/prod 네임스페이스는 둘 다
 * SPRING_PROFILES_ACTIVE=prod로 뜨지만(namespace로만 구분), local(docker-compose)은
 * application-local.yml의 표준 spring.datasource 하나만 쓴다. 여기서 프로파일을
 * 안 가르면 local이 app.datasource.writer/reader 없이 기동을 시도하다 실패한다.
 */
@Configuration
@Profile("prod")
public class RoutingDataSourceConfig {

    private static final String WRITER = "writer";
    private static final String READER = "reader";

    @Bean
    @ConfigurationProperties("app.datasource.writer")
    public HikariDataSource writerDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    @Bean
    @ConfigurationProperties("app.datasource.reader")
    public HikariDataSource readerDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    @Bean
    @Primary
    public DataSource dataSource(HikariDataSource writerDataSource, HikariDataSource readerDataSource) {
        AbstractRoutingDataSource routingDataSource = new AbstractRoutingDataSource() {
            @Override
            protected Object determineCurrentLookupKey() {
                return TransactionSynchronizationManager.isCurrentTransactionReadOnly() ? READER : WRITER;
            }
        };
        routingDataSource.setTargetDataSources(Map.of(WRITER, writerDataSource, READER, readerDataSource));
        routingDataSource.setDefaultTargetDataSource(writerDataSource);
        routingDataSource.afterPropertiesSet();

        return new LazyConnectionDataSourceProxy(routingDataSource);
    }
}
