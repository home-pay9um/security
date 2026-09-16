package kr.kro.pay9um.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 시간에 의존하는 컴포넌트가 일관되게 UTC를 사용하도록 제공하는 공통 빈입니다.
 * SecurityConfig와 분리해 인증 필터 구성 과정의 순환 참조를 방지합니다.
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
