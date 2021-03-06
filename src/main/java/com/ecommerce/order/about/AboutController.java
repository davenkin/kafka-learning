package com.ecommerce.order.about;

import brave.Span;
import brave.Tracer;
import brave.kafka.clients.KafkaTracing;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.core.env.Environment;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;
import java.util.Arrays;

@Slf4j
@RestController
@RequestMapping(value = "/about")
public class AboutController {
    //now
    private ZonedDateTime deployTime = ZonedDateTime.now();

    private Environment environment;

    private Consumer consumer;
    private Producer producer;

    private TaskExecutor taskExecutor;

    private KafkaTracing kafkaTracing;

    private Tracer tracer;


    public AboutController(Environment environment, Consumer consumer, Producer producer, TaskExecutor taskExecutor, KafkaTracing kafkaTracing, Tracer tracer) {
        this.environment = environment;
        this.consumer = consumer;
        this.producer = producer;
        this.taskExecutor = taskExecutor;
        this.kafkaTracing = kafkaTracing;
        this.tracer = tracer;
    }

    @GetMapping
    public AboutRepresentation about() {
        taskExecutor.execute(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    ConsumerRecords<String, String> records = consumer.poll(100);
                    for (ConsumerRecord<String, String> record : records) {
                        Span span = kafkaTracing.nextSpan((ConsumerRecord<?, ?>) record).name("on-message").start();
                        try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
                            producer.send(new ProducerRecord<String, String>("mytopic2", "hello2"));
                        } catch (Throwable t) {
                            span.tag("error", t.getMessage());
                            throw t;
                        } finally {
                            span.finish();
                        }
                    }
                }
            }
        });

        log.info("About api accessed.");
        String buildNumber = environment.getProperty("buildNumber");
        String buildTime = environment.getProperty("buildTime");
        String gitRevision = environment.getProperty("gitRevision");
        String gitBranch = environment.getProperty("gitBranch");

        String activeProfiles = Arrays.toString(environment.getActiveProfiles());
        String deployTime = this.deployTime.toString();
        return new AboutRepresentation(buildNumber, buildTime, deployTime, gitRevision, gitBranch, activeProfiles);
    }

}
