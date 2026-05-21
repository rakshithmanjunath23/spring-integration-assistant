package com.alip.assistant.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConceptExtractorServiceTest {

    private ConceptExtractorService service;

    @BeforeEach
    void setUp() {
        service = new ConceptExtractorService();
    }

    // --- extractConcepts tests ---

    @Test
    void shouldDetectIntegrationFlowConcept() {
        String content = """
                @Bean
                public IntegrationFlow holdingInquiryFlow() {
                    return IntegrationFlows.from("inputChannel")
                            .handle("serviceActivator", "process")
                            .get();
                }
                """;
        List<String> concepts = service.extractConcepts(content, "java");
        assertTrue(concepts.contains("IntegrationFlow"));
    }

    @Test
    void shouldDetectMessageChannelConcepts() {
        String content = """
                <int:channel id="requestChannel"/>
                <int:channel id="responseChannel"/>
                """;
        List<String> concepts = service.extractConcepts(content, "xml");
        assertTrue(concepts.contains("MessageChannel"));
    }

    @Test
    void shouldDetectDirectChannelInJava() {
        String content = """
                @Bean
                public MessageChannel inputChannel() {
                    return new DirectChannel();
                }
                """;
        List<String> concepts = service.extractConcepts(content, "java");
        assertTrue(concepts.contains("MessageChannel"));
    }

    @Test
    void shouldDetectRouterConcept() {
        String content = """
                <int:router input-channel="input" expression="headers['type']"/>
                """;
        List<String> concepts = service.extractConcepts(content, "xml");
        assertTrue(concepts.contains("Router"));
    }

    @Test
    void shouldDetectSplitterConcept() {
        String content = """
                @Splitter(inputChannel = "inputChannel")
                public List<Message<?>> split(Message<?> message) {
                    return splitMessage(message);
                }
                """;
        List<String> concepts = service.extractConcepts(content, "java");
        assertTrue(concepts.contains("Splitter"));
    }

    @Test
    void shouldDetectAggregatorConcept() {
        String content = """
                <int:aggregator input-channel="splitChannel"
                    output-channel="aggregatedChannel"/>
                """;
        List<String> concepts = service.extractConcepts(content, "xml");
        assertTrue(concepts.contains("Aggregator"));
    }

    @Test
    void shouldDetectGatewayConcept() {
        String content = """
                @MessagingGateway
                public interface OrderGateway {
                    void sendOrder(Order order);
                }
                """;
        List<String> concepts = service.extractConcepts(content, "java");
        assertTrue(concepts.contains("Gateway"));
    }

    @Test
    void shouldDetectServiceActivatorConcept() {
        String content = """
                @ServiceActivator(inputChannel = "processChannel")
                public void handleMessage(Message<?> message) {
                    // process
                }
                """;
        List<String> concepts = service.extractConcepts(content, "java");
        assertTrue(concepts.contains("ServiceActivator"));
    }

    @Test
    void shouldDetectTransformerConcept() {
        String content = """
                <int:transformer input-channel="raw" output-channel="transformed"
                    expression="payload.toUpperCase()"/>
                """;
        List<String> concepts = service.extractConcepts(content, "xml");
        assertTrue(concepts.contains("Transformer"));
    }

    @Test
    void shouldDetectPollerConcept() {
        String content = """
                @Bean
                public PollerMetadata defaultPoller() {
                    return Pollers.fixedDelay(1000).get();
                }
                """;
        List<String> concepts = service.extractConcepts(content, "java");
        assertTrue(concepts.contains("Poller"));
    }

    @Test
    void shouldDetectErrorChannelConcept() {
        String content = """
                <int:channel id="errorChannel"/>
                """;
        List<String> concepts = service.extractConcepts(content, "xml");
        assertTrue(concepts.contains("ErrorChannel"));
    }

    @Test
    void shouldDetectRetryAdviceConcept() {
        String content = """
                <bean id="retryAdvice" class="org.springframework.integration.handler.advice.RequestHandlerRetryAdvice">
                    <property name="retryTemplate" ref="retryTemplate"/>
                </bean>
                """;
        List<String> concepts = service.extractConcepts(content, "xml");
        assertTrue(concepts.contains("RetryAdvice"));
    }

    @Test
    void shouldDetectKafkaProtocol() {
        String content = """
                <int-kafka:outbound-channel-adapter kafka-template="kafkaTemplate"
                    topic="orders"/>
                """;
        List<String> concepts = service.extractConcepts(content, "xml");
        assertTrue(concepts.contains("Kafka"));
    }

    @Test
    void shouldDetectJmsProtocol() {
        String content = """
                <int-jms:message-driven-channel-adapter
                    channel="jmsInputChannel"
                    destination="requestQueue"/>
                """;
        List<String> concepts = service.extractConcepts(content, "xml");
        assertTrue(concepts.contains("JMS"));
    }

    @Test
    void shouldDetectHttpProtocol() {
        String content = """
                <int-http:inbound-gateway request-channel="httpInput"
                    path="/api/orders"/>
                """;
        List<String> concepts = service.extractConcepts(content, "xml");
        assertTrue(concepts.contains("HTTP"));
    }

    @Test
    void shouldDetectSftpProtocol() {
        String content = """
                <int-sftp:outbound-channel-adapter session-factory="sftpSessionFactory"
                    remote-directory="/upload"/>
                """;
        List<String> concepts = service.extractConcepts(content, "xml");
        assertTrue(concepts.contains("SFTP"));
    }

    @Test
    void shouldDetectSoapProtocol() {
        String content = """
                <int-ws:outbound-gateway uri="http://example.com/ws"
                    request-channel="wsRequest"/>
                """;
        List<String> concepts = service.extractConcepts(content, "xml");
        assertTrue(concepts.contains("SOAP"));
    }

    @Test
    void shouldDetectRestProtocol() {
        String content = """
                @RestController
                @RequestMapping("/api/v1")
                public class OrderController {
                }
                """;
        List<String> concepts = service.extractConcepts(content, "java");
        assertTrue(concepts.contains("REST"));
    }

    @Test
    void shouldDetectMultipleConcepts() {
        String content = """
                <int:gateway id="orderGateway" service-interface="com.example.OrderGateway"
                    default-request-channel="orderChannel"/>
                <int:channel id="orderChannel"/>
                <int:service-activator input-channel="orderChannel" ref="orderService"/>
                <int:router input-channel="routeChannel" expression="headers['type']"/>
                """;
        List<String> concepts = service.extractConcepts(content, "xml");
        assertTrue(concepts.contains("Gateway"));
        assertTrue(concepts.contains("MessageChannel"));
        assertTrue(concepts.contains("ServiceActivator"));
        assertTrue(concepts.contains("Router"));
    }

    @Test
    void shouldReturnEmptyListForNullContent() {
        List<String> concepts = service.extractConcepts(null, "java");
        assertTrue(concepts.isEmpty());
    }

    @Test
    void shouldReturnEmptyListForBlankContent() {
        List<String> concepts = service.extractConcepts("   ", "xml");
        assertTrue(concepts.isEmpty());
    }

    @Test
    void shouldReturnEmptyListForContentWithNoConcepts() {
        String content = "public class SimpleService { }";
        List<String> concepts = service.extractConcepts(content, "java");
        assertTrue(concepts.isEmpty());
    }

    // --- classifyIntegrationRole tests ---

    @Test
    void shouldClassifyAsGateway() {
        String content = """
                @MessagingGateway
                public interface PaymentGateway {
                    void process(Payment payment);
                }
                """;
        assertEquals("gateway", service.classifyIntegrationRole(content, "java"));
    }

    @Test
    void shouldClassifyAsRouter() {
        String content = """
                <int:router input-channel="input" expression="headers['routeKey']"/>
                """;
        assertEquals("router", service.classifyIntegrationRole(content, "xml"));
    }

    @Test
    void shouldClassifyAsTransformer() {
        String content = """
                @Transformer(inputChannel = "raw", outputChannel = "transformed")
                public String transform(String payload) {
                    return payload.toUpperCase();
                }
                """;
        assertEquals("transformer", service.classifyIntegrationRole(content, "java"));
    }

    @Test
    void shouldClassifyAsInboundAdapter() {
        String content = """
                <int:inbound-channel-adapter channel="fileInput"
                    expression="new java.io.File('/input')">
                    <int:poller fixed-delay="5000"/>
                </int:inbound-channel-adapter>
                """;
        assertEquals("inbound-adapter", service.classifyIntegrationRole(content, "xml"));
    }

    @Test
    void shouldClassifyAsOutboundAdapter() {
        String content = """
                <int:outbound-channel-adapter channel="fileOutput"
                    ref="fileWritingMessageHandler"/>
                """;
        assertEquals("outbound-adapter", service.classifyIntegrationRole(content, "xml"));
    }

    @Test
    void shouldReturnUnknownForUnclassifiableContent() {
        String content = "public class UtilityHelper { }";
        assertEquals("unknown", service.classifyIntegrationRole(content, "java"));
    }

    @Test
    void shouldReturnUnknownForNullContent() {
        assertEquals("unknown", service.classifyIntegrationRole(null, "java"));
    }

    // --- extractApiUrls tests ---

    @Test
    void shouldExtractHttpsUrl() {
        String content = """
                String endpoint = "https://api.example.com/v1/orders";
                """;
        List<String> urls = service.extractApiUrls(content);
        assertTrue(urls.contains("https://api.example.com/v1/orders"));
    }

    @Test
    void shouldExtractHttpUrl() {
        String content = """
                <int-http:outbound-gateway url="http://localhost:8080/api/process"
                    request-channel="httpRequest"/>
                """;
        List<String> urls = service.extractApiUrls(content);
        assertTrue(urls.stream().anyMatch(u -> u.contains("localhost:8080/api/process")));
    }

    @Test
    void shouldExtractUrlAttribute() {
        String content = """
                url="https://service.internal/api/v2/data"
                """;
        List<String> urls = service.extractApiUrls(content);
        assertTrue(urls.contains("https://service.internal/api/v2/data"));
    }

    @Test
    void shouldExtractUriAttribute() {
        String content = """
                uri="https://gateway.example.com/ws/endpoint"
                """;
        List<String> urls = service.extractApiUrls(content);
        assertTrue(urls.contains("https://gateway.example.com/ws/endpoint"));
    }

    @Test
    void shouldDeduplicateUrls() {
        String content = """
                url="https://api.example.com/orders"
                uri="https://api.example.com/orders"
                """;
        List<String> urls = service.extractApiUrls(content);
        long count = urls.stream()
                .filter(u -> u.equals("https://api.example.com/orders"))
                .count();
        assertEquals(1, count);
    }

    @Test
    void shouldReturnEmptyListForNullContentUrls() {
        List<String> urls = service.extractApiUrls(null);
        assertTrue(urls.isEmpty());
    }

    @Test
    void shouldReturnEmptyListForContentWithNoUrls() {
        String content = "public class NoUrls { int x = 5; }";
        List<String> urls = service.extractApiUrls(content);
        assertTrue(urls.isEmpty());
    }
}
