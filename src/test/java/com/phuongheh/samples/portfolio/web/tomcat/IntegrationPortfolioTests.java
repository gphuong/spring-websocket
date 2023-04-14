package com.phuongheh.samples.portfolio.web.tomcat;

import com.phuongheh.samples.portfolio.PortfolioPosition;
import com.phuongheh.samples.portfolio.config.DispatcherServletInitializer;
import com.phuongheh.samples.portfolio.config.WebConfig;
import com.phuongheh.samples.portfolio.config.WebSecurityInitializer;
import com.phuongheh.samples.portfolio.service.Trade;
import com.phuongheh.samples.portfolio.web.support.TomcatWebSocketTestServer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.util.JsonPathExpectationsHelper;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.SocketUtils;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.config.annotation.AbstractWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.server.standard.TomcatRequestUpgradeStrategy;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.sockjs.client.RestTemplateXhrTransport;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class IntegrationPortfolioTests {
    private static Log logger = LogFactory.getLog(IntegrationPortfolioTests.class);
    private static int port;
    private static TomcatWebSocketTestServer server;
    private static SockJsClient sockJsClient;
    private static final WebSocketHttpHeaders headers = new WebSocketHttpHeaders();

    @BeforeClass
    public static void setup() throws Exception {
        System.setProperty("spring.profiles.active", "test.tomcat");
        port = SocketUtils.findAvailableTcpPort();
        server = new TomcatWebSocketTestServer(port);
        server.deployConfig(TestDispatcherServletInitializer.class, WebSecurityInitializer.class);
        server.start();

        loginAndSaveJsessionIdCookie("fabrice", "fab123", headers);

        List<Transport> transports = new ArrayList<>();
        transports.add(new WebSocketTransport(new StandardWebSocketClient()));
        RestTemplateXhrTransport xhrTransport = new RestTemplateXhrTransport(new RestTemplate());
        transports.add(xhrTransport);

        sockJsClient = new SockJsClient(transports);
    }

    @AfterClass
    public static void teardown() {
        if (server != null) {
            try {
                server.undeployConfig();
            } catch (Throwable t) {
                logger.error("Failed to undeploy application", t);
            }
            try {
                server.stop();
            } catch (Throwable t) {
                logger.error("Failed to stop server", t);
            }
        }
    }

    @Test
    public void getPositions() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        StompSessionHandler handler = new AbstractTestSessionHandler(failure) {
            @Override
            public void afterConnected(final StompSession session, StompHeaders connectedHeaders) {
                session.subscribe("/app/positions", new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders stompHeaders) {
                        return byte[].class;
                    }

                    @Override
                    public void handleFrame(StompHeaders stompHeaders, Object payload) {
                        String json = new String((byte[]) payload);
                        logger.debug("Got" + json);
                        try {
                            new JsonPathExpectationsHelper("$[0].company").assertValue(json, "Citrix Systems, Inc");
                            new JsonPathExpectationsHelper("$[1].company").assertValue(json, "Dell Inc.");
                            new JsonPathExpectationsHelper("$[2].company").assertValue(json, "Microsoft");
                            new JsonPathExpectationsHelper("$[3].company").assertValue(json, "Oracle");
                        } catch (Throwable t) {
                            failure.set(t);
                        } finally {
                            session.disconnect();
                            latch.countDown();
                        }
                    }
                });
            }
        };

        WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.connect("ws://localhost:{port}/portfolio", this.headers, handler, port);
        if (failure.get() != null) {
            throw new AssertionError("", failure.get());
        }

        if (!latch.await(5, TimeUnit.SECONDS)) {
            fail("Portfolio positions not received");
        }
    }

    @Test
    public void executeTrade() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        StompSessionHandler handler = new AbstractTestSessionHandler(failure) {
            @Override
            public void afterConnected(final StompSession session, StompHeaders connectedHeaders) {
                session.subscribe("/user/queue/position-updates", new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders stompHeaders) {
                        return PortfolioPosition.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders stompHeaders, Object payload) {
                        PortfolioPosition position = (PortfolioPosition) payload;
                        logger.debug("Got" + position);
                        try {
                            assertEquals(75, position.getShares());
                            assertEquals("Dell Inc.", position.getCompany());
                        } catch (Throwable t) {
                            failure.set(t);
                        } finally {
                            session.disconnect();
                            latch.countDown();
                        }
                    }
                });
                try {
                    Trade trade = new Trade();
                    trade.setAction(Trade.TradeAction.Buy);
                    trade.setTicker("DELL");
                    trade.setShares(25);
                    session.send("/app/trade", trade);
                }catch (Throwable t){
                    failure.set(t);
                    latch.countDown();
                }
            }
        };
        WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        stompClient.connect("ws://localhost:{port}/portfolio", headers, handler, port);
        if(!latch.await(10, TimeUnit.SECONDS)){
            fail("Trade confirmation not received");
        }        else if(failure.get()!=null){
            throw new AssertionError("", failure.get());
        }
    }

    public static class TestDispatcherServletInitializer extends DispatcherServletInitializer {
        @Override
        protected Class<?>[] getServletConfigClasses() {
            return new Class[]{WebConfig.class, TestWebSocketConfig.class};
        }
    }

    private static void loginAndSaveJsessionIdCookie(final String user, final String password, final HttpHeaders headersToUpdate) {
        String url = "http://localhost:" + port + "/login.html";
        new RestTemplate().execute(url, HttpMethod.POST, new RequestCallback() {
            @Override
            public void doWithRequest(ClientHttpRequest clientHttpRequest) throws IOException {
                MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
                map
                        .add("username", user);
                map.add("password", password);
                new FormHttpMessageConverter().write(map, MediaType.APPLICATION_FORM_URLENCODED, clientHttpRequest);
            }
        }, new ResponseExtractor<Object>() {
            @Override
            public Object extractData(ClientHttpResponse response) throws IOException {
                headersToUpdate.add("Cookie", response.getHeaders().getFirst("Set-Cookie"));
                return null;
            }
        });
    }

    @Configuration
    @EnableScheduling
    @ComponentScan(basePackages = "com.phuongheh.samples", excludeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, value = Configuration.class))
    @EnableWebSocketMessageBroker
    static class TestWebSocketConfig extends AbstractWebSocketMessageBrokerConfigurer {
        @Autowired
        Environment env;

        @Override
        public void registerStompEndpoints(StompEndpointRegistry registry) {
            DefaultHandshakeHandler handler = new DefaultHandshakeHandler(new TomcatRequestUpgradeStrategy());
            registry.addEndpoint("/portfolio").setHandshakeHandler(handler).withSockJS();
        }

        @Override
        public void configureMessageBroker(MessageBrokerRegistry registry) {
            registry.enableStompBrokerRelay("/queue/", "/topic/");
            registry.setApplicationDestinationPrefixes("/app");
        }
    }

    private static abstract class AbstractTestSessionHandler extends StompSessionHandlerAdapter {
        private final AtomicReference<Throwable> failure;

        public AbstractTestSessionHandler(AtomicReference<Throwable> failure) {
            this.failure = failure;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            logger.error("STOMP ERROR frame: " + headers.toString());
            this.failure.set(new Exception(headers.toString()));
        }

        @Override
        public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
            logger.error("Handler exception", exception);
            this.failure.set(exception);
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            logger.error("Transport failure", exception);
            this.failure.set(exception);
        }
    }

}
