package app.ImageTask;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
public abstract class AbstractMongoTest {

    @Autowired
    protected ReactiveMongoTemplate reactiveMongoTemplate;


    @Container
    protected static final MongoDBContainer MONGO_DB_CONTAINER = new MongoDBContainer("mongo:4.4.2")
            .withExposedPorts(27017)
            .withReuse(true);

    @BeforeAll
    static void setupDatabase() {
        MONGO_DB_CONTAINER.start();
        System.setProperty("spring.data.mongodb.uri", MONGO_DB_CONTAINER.getReplicaSetUrl());
    }
}
