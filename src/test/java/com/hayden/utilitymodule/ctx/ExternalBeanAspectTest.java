package com.hayden.utilitymodule.ctx;

import com.hayden.utilitymodule.log.FluentDConfigProperties;
import lombok.Getter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicBoolean;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@EnableAspectJAutoProxy
@Import({ExternalBeanAspectTest.TestExternalBean.class, FluentDConfigProperties.class})
public class ExternalBeanAspectTest {

    @SpringBootApplication
    public static class DummyApp {
        public static void main(String[] args) {
            SpringApplication.run(DummyApp.class, args);
        }
    }

    @Getter
    @Component
    public static class TestExternalBean implements ExternalBean<TestExternalBean> {
        private final AtomicBoolean set = new AtomicBoolean(false);

        public void doSomething() {}
    }

    @Autowired
    TestExternalBean testExternalBean;

    @Test
    void doAutowire() {
        testExternalBean.doSomething();
        Assertions.assertTrue(testExternalBean.getSet().get());
        AtomicBoolean s = (AtomicBoolean) ReflectionTestUtils.getField(testExternalBean, "set");
        Assertions.assertTrue(s.get());
        var u = ExternalBean.underlying(testExternalBean);
        Assertions.assertTrue(u.set.get());
    }

    @Test
    void testWithout() {
//        FluentDRestTemplateSender fluentDRestTemplateSender = new FluentDRestTemplateSender();
//        fluentDRestTemplateSender.autowireBean();
    }

}