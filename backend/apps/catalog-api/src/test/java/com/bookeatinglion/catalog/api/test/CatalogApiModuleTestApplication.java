package com.bookeatinglion.catalog.api.test;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SecurityConfig 슬라이스 테스트 전용 부트 클래스.
 *
 * CatalogApiApplication을 그대로 쓰지 않는 이유: 거기 붙은 @EnableFeignClients /
 * @EnableJpaRepositories 는 @WebMvcTest 슬라이스에서도 활성화되어 Feign/DataSource가
 * 필요해진다. 이 패키지(com.bookeatinglion.catalog.api.test)는 비어 있어서 기본
 * 컴포넌트 스캔으로는 아무것도 안 걸리고, 필요한 SecurityConfig는 테스트에서
 * @Import로 직접 가져온다.
 */
@SpringBootApplication
public class CatalogApiModuleTestApplication {
}
