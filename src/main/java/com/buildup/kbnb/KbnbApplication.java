package com.buildup.kbnb;

import com.buildup.kbnb.util.payment.BootPayApi;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.modelmapper.ModelMapper;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class KbnbApplication {

    public static final String APPLICATION_LOCATIONS = "spring.config.location="
            + "classpath:application.yml,"
            + "optional:classpath:secret.yml";

    public static void main(String[] args) {
//        SpringApplication.run(KbnbApplication.class, args);
        new SpringApplicationBuilder(KbnbApplication.class)
                .properties(APPLICATION_LOCATIONS)
                .run(args);
    }

    @Bean
    public ModelMapper modelMapper() {
        return new ModelMapper();
    }

    @Bean
    public RestTemplate restTemplate() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setReadTimeout(5000); // 읽기시간초과, ms
        factory.setConnectTimeout(3000); // 연결시간초과, ms
        HttpClient httpClient = HttpClientBuilder.create() .setMaxConnTotal(100) // connection pool 적용
                    .setMaxConnPerRoute(5) // connection pool 적용
                    .build();
        factory.setHttpClient(httpClient); // 동기실행에 사용될 HttpClient 세팅
        return new RestTemplate(factory);
    }
}
