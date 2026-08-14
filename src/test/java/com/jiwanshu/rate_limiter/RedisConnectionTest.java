package com.jiwanshu.rate_limiter;

import ch.qos.logback.core.net.SyslogOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class RedisConnectionTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void canConnectAndWriteReadFromRedis(){
      redisTemplate.opsForValue().set("connection-test-key", "Hello Redis");
      String value = redisTemplate.opsForValue().get("connection-test-key");

      assertEquals("Hello Redis",value);
      System.out.println("Redis Connection Successful , value returned  "+value);
    }
}
