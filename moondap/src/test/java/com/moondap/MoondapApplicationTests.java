package com.moondap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 애플리케이션 컨텍스트가 정상적으로 뜨는지 확인한다.
 *
 * 설정값은 src/test/resources/application-test.properties 에서 공급한다.
 * 개발자 머신의 환경변수에 의존하지 않도록 반드시 test 프로파일을 활성화한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class MoondapApplicationTests {

	@Test
	void contextLoads() {
	}

}
