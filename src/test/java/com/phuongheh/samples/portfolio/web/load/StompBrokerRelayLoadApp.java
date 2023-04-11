package com.phuongheh.samples.portfolio.web.load;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.broker.BrokerAvailabilityEvent;
import org.springframework.messaging.simp.config.AbstractMessageBrokerConfiguration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.StopWatch;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.socket.messaging.DefaultSimpUserRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class StompBrokerRelayLoadApp {
    public static final int NUMBER_OF_USERS = 250;
    public static final int NUMBER_OF_MESSAGES_TO_BROADCAST = 100;
    public static final String DEFAULT_DESTINATION = "/topic/brokerTests-global";

    private AbstractSubscribableChannel clientInboundChannel;
    private TestMessageHandler clientOutboundMessageHandler;
    private SimpMessagingTemplate brokerMessagingTemplate;
    private StopWatch stopWatch;

    public static void main(String[] args) {
        StompBrokerRelayLoadApp app = new StompBrokerRelayLoadApp();
        try {
            app.runTest();
        } catch (Throwable t) {
            t.printStackTrace();
        }
        System.exit(0);
    }

    private void runTest() throws InterruptedException {
        AnnotationConfigWebApplicationContext cxt = new AnnotationConfigWebApplicationContext();
        cxt.register(MessageConfig.class);
        cxt.refresh();

        this.clientInboundChannel = cxt.getBean("clientInboundChannel", AbstractSubscribableChannel.class);
        this.clientOutboundMessageHandler = cxt.getBean(TestMessageHandler.class);
        this.brokerMessagingTemplate = cxt.getBean(SimpMessagingTemplate.class);

        this.stopWatch = new StopWatch("STOMP Broker Relay Load Tests");

        CountDownLatch brokerAvailabilityLatch = cxt.getBean(CountDownLatch.class);
        brokerAvailabilityLatch.await(5000, TimeUnit.MILLISECONDS);

        List<String> sessionIds = generateIds("session", NUMBER_OF_USERS);
        List<String> subscriptionIds = generateIds("subscription", NUMBER_OF_USERS);
        List<String> receiptIds = generateIds("receipt", NUMBER_OF_USERS);

        connect(sessionIds);
        subscribe(sessionIds, subscriptionIds, receiptIds);

        Person person = new Person();
        person.setName("Joe");
        broadcast(DEFAULT_DESTINATION, person, NUMBER_OF_MESSAGES_TO_BROADCAST, NUMBER_OF_USERS);
        disconnect(sessionIds);
    }

    private void broadcast(String destination, Person person, int sendCount, int numberOfSubscribers) throws InterruptedException {
        System.out.println("Broacasting " + sendCount + " messages to " + numberOfSubscribers + " users ");
        this.stopWatch.start();
        for (int i = 0; i < sendCount; i++) {
            this.brokerMessagingTemplate.convertAndSend(destination, person);
        }
        int remaining = sendCount * numberOfSubscribers;
        while (remaining > 0) {
            Message<?> message = this.clientOutboundMessageHandler.awaitMessage(5000);
            assertNotNull("No more messages, expected " + remaining + " more id(s)", message);
            StompHeaderAccessor headers = StompHeaderAccessor.wrap(message);
            assertEquals(StompCommand.MESSAGE, headers.getCommand());
            assertEquals(destination, headers.getDestination());
            assertEquals("{\"name\":\"Joe\"}", new java.lang.String((byte[]) message.getPayload()));
            remaining--;
            if (remaining % 10000 == 0) {
                System.out.println(".");
            }
        }
        this.stopWatch.start();
        System.out.println("(" + this.stopWatch.getLastTaskTimeMillis() + " millis)");
    }

    private void disconnect(List<String> sessionIds) {
        System.out.println("Disconnecting... ");
        this.stopWatch.start("Disconnect");

        for (String sessionId : sessionIds) {
            StompHeaderAccessor headerAccessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
            headerAccessor.setSessionId(sessionId);
            Message<byte[]> message = MessageBuilder.createMessage(new byte[0], headerAccessor.getMessageHeaders());
            this.clientInboundChannel.send(message);
        }
        this.stopWatch.stop();
        System.out.println("(" + this.stopWatch.getLastTaskTimeMillis() + " millis)");
    }

    static class Person {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    private void subscribe(List<String> sessionIds,
                           List<String> subscriptionIds,
                           List<String> receiptIds) throws InterruptedException {
        System.out.println("Subscribing all users");
        this.stopWatch.start();
        for (int i = 0; i < sessionIds.size(); i++) {
            StompHeaderAccessor headerAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
            headerAccessor.setSessionId(sessionIds.get(i));
            headerAccessor.setSubscriptionId(subscriptionIds.get(i));
            headerAccessor.setDestination(DEFAULT_DESTINATION);
            headerAccessor.setReceipt(receiptIds.get(i));
            Message<byte[]> message = MessageBuilder.createMessage(new byte[0], headerAccessor.getMessageHeaders());
            this.clientInboundChannel.send(message);
        }

        List<String> expectedIds = new ArrayList<>();
        while (!expectedIds.isEmpty()) {
            Message<?> message = this.clientOutboundMessageHandler.awaitMessage(5000);
            assertNotNull("No more messages, expected " + expectedIds.size() + " more ids: " + expectedIds, message);
            StompHeaderAccessor headers = StompHeaderAccessor.wrap(message);
            assertEquals(StompCommand.RECEIPT, headers.getCommand());
            assertTrue(expectedIds.remove(headers.getReceiptId()));
            if (expectedIds.size() % 100 == 0) {
                System.out.print(".");
            }
        }
        this.stopWatch.stop();
        System.out.println("(" + this.stopWatch.getLastTaskTimeMillis() + " millis");
    }

    private void connect(List<String> sessionIds) throws InterruptedException {
        System.out.println("Connecting " + sessionIds.size() + " users ");
        this.stopWatch.start();

        for (String sessionId : sessionIds) {
            StompHeaderAccessor headerAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            headerAccessor.setHeartbeat(0, 0);
            headerAccessor.setSessionId(sessionId);
            Message<byte[]> message = MessageBuilder.createMessage(new byte[0], headerAccessor.getMessageHeaders());
            this.clientInboundChannel.send(message);
        }

        List<String> expectedIds = new ArrayList<>();
        while (!expectedIds.isEmpty()) {
            Message<?> message = this.clientOutboundMessageHandler.awaitMessage(5000);
            assertNotNull("No more messages, expected " + expectedIds.size() + " more ids: " + expectedIds, message);
            StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(message);
            assertEquals(StompCommand.CONNECTED, headerAccessor.getCommand());
            assertTrue(expectedIds.remove(headerAccessor.getSessionId()));
            if (expectedIds.size() % 100 == 0) {
                System.out.println(".");
            }
        }
        this.stopWatch.stop();
        System.out.println(" (" + this.stopWatch.getLastTaskTimeMillis() + " millis");
    }

    private List<String> generateIds(String idPrefix, int count) {
        List<String> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(idPrefix + i);
        }
        return Collections.unmodifiableList(ids);
    }

    @Configuration
    static class MessageConfig extends AbstractMessageBrokerConfiguration
            implements ApplicationListener<ApplicationEvent> {
        private final CountDownLatch brokerAvailabilityLatch = new CountDownLatch(1);

        @Override
        public void onApplicationEvent(ApplicationEvent event) {
            if (event instanceof ContextRefreshedEvent) {
                simpAnnotationMethodMessageHandler().stop();
                userDestinationMessageHandler().stop();
                clientOutboundChannel().subscribe(clientOutboundMessageHandler());
            } else if (event instanceof BrokerAvailabilityEvent) {
                this.brokerAvailabilityLatch.countDown();
            }
        }

        private MessageHandler clientOutboundMessageHandler() {
            return new TestMessageHandler();
        }

        @Override
        protected void configureMessageBroker(MessageBrokerRegistry registry) {
            registry.enableStompBrokerRelay("/topic/");
        }

        @Override
        protected void configureClientInboundChannel(ChannelRegistration registration) {
            registration.taskExecutor().corePoolSize(4);
        }

        @Override
        protected void configureClientOutboundChannel(ChannelRegistration registration) {
            registration.taskExecutor().corePoolSize(4);
        }

        @Override
        protected SimpUserRegistry createLocalUserRegistry() {
            return new DefaultSimpUserRegistry();
        }
    }

    static class TestMessageHandler implements MessageHandler {
        private final BlockingQueue<Message<?>> messages = new LinkedBlockingQueue<>();

        public Message<?> awaitMessage(long timeoutInMillis) throws InterruptedException {
            return this.messages.poll(timeoutInMillis, TimeUnit.MILLISECONDS);
        }

        @Override
        public void handleMessage(Message<?> message) throws MessagingException {
            this.messages.add(message);
        }
    }
}
