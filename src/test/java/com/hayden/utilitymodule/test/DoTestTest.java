package com.hayden.utilitymodule.test;

//import com.hayden.inject_fields.AutowireParameter;
//import com.hayden.inject_fields.InjectFieldsAutoConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@SpringBootTest
@ExtendWith(SpringExtension.class)
//@Import(InjectFieldsAutoConfiguration.class)
@EnableAspectJAutoProxy
class DoTestTest {

    @SpringBootApplication
    public static class TestApplication {
        public static void main(String[] args) {
            SpringApplication.run(TestApplication.class, args);
        }
    }

    @Autowired
    DoTest doTest;

    @Test
    void doTest() {
//        var to = new com.hayden.utilitymodule.test.TestObject();
//        to = doTest.doTest(to);
//        Assertions.assertNotNull(to.getTestBean());
//        var another = doTest.doTest(AutowireParameter.AUTO.PRM.INJECT_ME());
//        Assertions.assertNotNull(another.getTestBean());
    }
}