package com.phuongheh.samples.portfolio.web.standalone;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phuongheh.samples.portfolio.service.PortfolioService;
import com.phuongheh.samples.portfolio.service.PortfolioServiceImpl;
import com.phuongheh.samples.portfolio.service.Trade;
import com.phuongheh.samples.portfolio.web.PortfolioController;
import com.phuongheh.samples.portfolio.web.support.TestPrincipal;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.StaticApplicationContext;

import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.support.SimpAnnotationMethodMessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.Message;

import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.util.JsonPathExpectationsHelper;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;

public class StandalonePortfolioControllerTests {
    private PortfolioService portfolioService;
    private TestTradeService tradeService;
    private TestMessageChannel clientOutboundChannel;
    private TestAnnotationMethodHandler annotationMethodHandler;

    @Before
    public void setUp() {
        this.portfolioService = new PortfolioServiceImpl();
        this.tradeService = new TestTradeService();
        PortfolioController controller = new PortfolioController(this.portfolioService, this.tradeService);

        this.clientOutboundChannel = new TestMessageChannel();
        this.annotationMethodHandler = new TestAnnotationMethodHandler(new TestMessageChannel(), clientOutboundChannel, new SimpMessagingTemplate(new TestMessageChannel()));
        this.annotationMethodHandler.registerHandler(controller);
        this.annotationMethodHandler.setDestinationPrefixes(Arrays.asList("/app"));
        this.annotationMethodHandler.setMessageConverter(new MappingJackson2MessageConverter());
        this.annotationMethodHandler.setApplicationContext(new StaticApplicationContext());
        this.annotationMethodHandler.afterPropertiesSet();
    }

    @Test
    public void getPosition(){
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setSubscriptionId("0");
        headers.setDestination("/app/positions");
        headers.setSessionId("0");
        headers.setUser(new TestPrincipal("fabrice"));
        headers.setSessionAttributes(new HashMap<String, Object>());
        Message<byte[]> message = MessageBuilder.withPayload(new byte[0]).setHeaders(headers).build();

        this.annotationMethodHandler.handleMessage(message);

        assertEquals(1, this.clientOutboundChannel.getMessages().size());
        Message<?> reply = this.clientOutboundChannel.getMessages().get(0);

        StompHeaderAccessor replyHeaders = StompHeaderAccessor.wrap(reply);
        assertEquals("0", replyHeaders.getSessionId());
        assertEquals("0", replyHeaders.getSubscriptionId());
        assertEquals("/app/positions", replyHeaders.getDestination());

        String json = new String((byte[]) reply.getPayload(), Charset.forName("UTF-8"));
        new JsonPathExpectationsHelper("$[0].company").assertValue(json, "Citrix Systems, Inc.");
        new JsonPathExpectationsHelper("$[1].company").assertValue(json, "Dell Inc.");
        new JsonPathExpectationsHelper("$[2].company").assertValue(json, "Microsoft");
        new JsonPathExpectationsHelper("$[3].company").assertValue(json, "Oracle");
    }

    @Test
    public void executeTrade() throws JsonProcessingException {
        Trade trade = new Trade();
        trade.setAction(Trade.TradeAction.Buy);
        trade.setTicker("DELL");
        trade.setShares(25);

        byte[] payload = new ObjectMapper().writeValueAsBytes(trade);

        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SEND);
        headers.setDestination("/app/trade");
        headers.setSessionId("0");
        headers.setUser(new TestPrincipal("fabrice"));
        headers.setSessionAttributes(new HashMap<String, Object>());
        Message<byte[]> message = MessageBuilder.withPayload(payload).setHeaders(headers).build();

        this.annotationMethodHandler.handleMessage(message);

        assertEquals(1, this.tradeService.getTrades().size());
        Trade actual = this.tradeService.getTrades().get(0);

        assertEquals(Trade.TradeAction.Buy, actual.getAction());
        assertEquals("DELL", actual.getTicker());
        assertEquals(25, actual.getShares());
        assertEquals("fabrice", actual.getUsername());
    }
    private static class TestAnnotationMethodHandler extends SimpAnnotationMethodMessageHandler {

        public TestAnnotationMethodHandler(SubscribableChannel clientInboundChannel,
                                           MessageChannel clientOutboundChannel,
                                           SimpMessageSendingOperations brokerTemplate) {
            super(clientInboundChannel, clientOutboundChannel, brokerTemplate);
        }

        public void registerHandler(Object handler) {
            super.detectHandlerMethods(handler);
        }
    }
}
