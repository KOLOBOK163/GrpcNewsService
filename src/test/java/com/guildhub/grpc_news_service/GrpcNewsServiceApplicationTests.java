package com.guildhub.grpc_news_service;

import com.guildhub.grpc_news_service.grpc.MinioService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
class GrpcNewsServiceApplicationTests {

	@MockBean
	private MinioService minioService;

	@Test
	void contextLoads() {
	}

}
