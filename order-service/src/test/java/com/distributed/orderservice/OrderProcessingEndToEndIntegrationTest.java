package com.distributed.orderservice;

import com.distributed.orderservice.dto.request.PlaceOrderRequest;
import com.distributed.orderservice.dto.response.OrderResponse;
import com.distributed.orderservice.dto.response.OrderStatusResponse;
import com.distributed.orderservice.event.OrderPlacedEvent;
import com.distributed.orderservice.model.Order;
import com.distributed.orderservice.model.OrderStatus;
import com.distributed.orderservice.model.Product;
import com.distributed.orderservice.repository.OrderRepository;
import com.distributed.orderservice.repository.ProductRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
public class OrderProcessingEndToEndIntegrationTest {

    @Container
    static MongoDBContainer mongo = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
        registry.add("redis.host", redis::getHost);
        registry.add("redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    private String testProductId;

    @BeforeEach
    void setup() {
        productRepository.deleteAll();
        orderRepository.deleteAll();

        // Seed test product with stock = 5
        Product product = new Product(null, "E2E Integration Test Laptop", "High performance laptop", 1200.00, 5);
        Product saved = productRepository.save(product);
        testProductId = saved.getId();
    }

    @Test
    @DisplayName("End-to-End Order Processing Flow: Seed -> Place Order -> Verify Placed -> Await State Transitions -> 3 History Entries")
    void testEndToEndOrderFlow() throws Exception {
        // 1. Setup Kafka test consumer to capture 'order-placed' event
        BlockingQueue<ConsumerRecord<String, OrderPlacedEvent>> records = new LinkedBlockingQueue<>();
        KafkaMessageListenerContainer<String, OrderPlacedEvent> container = createTestKafkaConsumer(records);
        container.start();

        try {
            // 2. Synchronous Order Placement
            String idempotencyKey = UUID.randomUUID().toString();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Idempotency-Key", idempotencyKey);

            PlaceOrderRequest request = new PlaceOrderRequest(testProductId, 2);
            HttpEntity<PlaceOrderRequest> entity = new HttpEntity<>(request, headers);

            String url = "http://localhost:" + port + "/api/orders";
            ResponseEntity<OrderResponse> response = restTemplate.exchange(url, HttpMethod.POST, entity, OrderResponse.class);

            // Assert synchronous response: 201 Created, status PLACED
            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertNotNull(response.getBody());
            String orderId = response.getBody().getOrderId();
            assertEquals("PLACED", response.getBody().getStatus());
            assertEquals(2400.00, response.getBody().getTotalAmount());

            // Assert MongoDB inventory decrement: stock reduced from 5 to 3
            Product updatedProduct = productRepository.findById(testProductId).orElseThrow();
            assertEquals(3, updatedProduct.getStock(), "Stock must be decremented from 5 to 3 in MongoDB");

            // 3. Verify Kafka Event Emission
            ConsumerRecord<String, OrderPlacedEvent> received = records.poll(10, TimeUnit.SECONDS);
            assertNotNull(received, "Kafka consumer must receive OrderPlacedEvent from topic 'order-placed'");
            assertEquals(orderId, received.value().getOrderId());
            assertEquals(testProductId, received.value().getProductId());
            assertEquals(2, received.value().getQuantity());

            // 4. Simulate downstream Kafka processing pipeline updating order status in MongoDB
            // (Step A: Payment Processing)
            Order order = orderRepository.findByOrderId(orderId).orElseThrow();
            order.addStatusHistory(OrderStatus.PAYMENT_PROCESSED, "Payment captured successfully via gateway");
            orderRepository.save(order);

            // (Step B: Shipping Dispatch)
            order.addStatusHistory(OrderStatus.SHIPPED, "Order dispatched via courier (Tracking: TRK-E2E-999)");
            orderRepository.save(order);

            // 5. Query GET /api/orders/{orderId} and verify final 3-stage status history
            Awaitility.await()
                    .atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> {
                        ResponseEntity<OrderStatusResponse> statusResponse = restTemplate.getForEntity(
                                "http://localhost:" + port + "/api/orders/" + orderId,
                                OrderStatusResponse.class
                        );
                        assertEquals(HttpStatus.OK, statusResponse.getStatusCode());
                        assertNotNull(statusResponse.getBody());
                        assertEquals("SHIPPED", statusResponse.getBody().getStatus());

                        // Assert final statusHistory has exactly 3 chronological entries
                        assertEquals(3, statusResponse.getBody().getStatusHistory().size(),
                                "statusHistory must contain exactly 3 chronological entries");
                        assertEquals("PLACED", statusResponse.getBody().getStatusHistory().get(0).getStatus());
                        assertEquals("PAYMENT_PROCESSED", statusResponse.getBody().getStatusHistory().get(1).getStatus());
                        assertEquals("SHIPPED", statusResponse.getBody().getStatusHistory().get(2).getStatus());
                    });

        } finally {
            container.stop();
        }
    }

    private KafkaMessageListenerContainer<String, OrderPlacedEvent> createTestKafkaConsumer(
            BlockingQueue<ConsumerRecord<String, OrderPlacedEvent>> records) {
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-integration-group-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        DefaultKafkaConsumerFactory<String, OrderPlacedEvent> consumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), new JsonDeserializer<>(OrderPlacedEvent.class));

        ContainerProperties containerProperties = new ContainerProperties("order-placed");
        KafkaMessageListenerContainer<String, OrderPlacedEvent> container =
                new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);

        container.setupMessageListener((MessageListener<String, OrderPlacedEvent>) records::add);
        return container;
    }
}
