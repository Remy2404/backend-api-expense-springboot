package com.wing.backendapiexpensespringboot;

import com.wing.backendapiexpensespringboot.config.DatabaseEnvironmentInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendApiExpenseSpringbootApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(BackendApiExpenseSpringbootApplication.class);
        application.addInitializers(new DatabaseEnvironmentInitializer());
        application.run(args);
    }

}
