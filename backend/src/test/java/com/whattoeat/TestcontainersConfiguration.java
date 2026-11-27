package com.whattoeat;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	/** latest는 시간이 지나면 버전이 바뀌므로, 로컬 compose.yaml·배포와 같은 버전으로 고정한다. */
	static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:9.7");

	@Bean
	@ServiceConnection
	MySQLContainer mysqlContainer() {
		return new MySQLContainer(MYSQL_IMAGE);
	}

}
