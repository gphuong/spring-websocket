package com.phuongheh.samples.portfolio.web.load;

import com.phuongheh.samples.portfolio.web.support.JettyWebSocketTestServer;
import com.phuongheh.samples.portfolio.web.support.TomcatWebSocketTestServer;
import com.phuongheh.samples.portfolio.web.support.WebSocketTestServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.DefaultContentTypeResolver;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.SocketUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.socket.config.WebSocketMessageBrokerStats;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurationSupport;
import org.springframework.web.socket.server.jetty.JettyRequestUpgradeStrategy;
import org.springframework.web.socket.server.standard.TomcatRequestUpgradeStrategy;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class StompWebSocketLoadTestServer {
    public static final boolean USE_JETTY = false;
    private static final StringMessageConverter MESSAGE_CONVERTER;

    static {
        DefaultContentTypeResolver resolver = new DefaultContentTypeResolver();
        resolver.setDefaultMimeType(MimeTypeUtils.TEXT_PLAIN);

        MESSAGE_CONVERTER = new StringMessageConverter();
        MESSAGE_CONVERTER.setContentTypeResolver(resolver);
    }

    public static void main(String[] args) throws Exception {
        WebSocketTestServer server = null;

        try {
            AnnotationConfigWebApplicationContext cxt = new AnnotationConfigWebApplicationContext();
            cxt.register(WebSocketConfig.class);

            int port = SocketUtils.findAvailableTcpPort();
            if (USE_JETTY) {
                server = new JettyWebSocketTestServer(port);
            } else {
                System.setProperty("spring.profiles.active", "test.tomcat");
                server = new TomcatWebSocketTestServer(port);
            }
            server.deployConfig(cxt);
            server.start();

            System.out.println("Running on port " + port);
            System.out.println("Press any key to stop");
            System.in.read();
            if (server != null) {
                try {
                    server.undeployConfig();
                } catch (Throwable t) {
                    System.err.println("Failed to undeploy application");
                    t.printStackTrace();
                }
                try {
                    server.stop();
                    ;
                } catch (Throwable t) {
                    System.err.println("Failed to stop server");
                    t.printStackTrace();
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        System.exit(0);
    }

    @Configuration
    @EnableWebMvc
    @EnableScheduling
    private static class WebSocketConfig extends WebSocketMessageBrokerConfigurationSupport {

        @Override
        protected void registerStompEndpoints(StompEndpointRegistry registry) {
            DefaultHandshakeHandler handler = USE_JETTY ?
                    new DefaultHandshakeHandler(new JettyRequestUpgradeStrategy()) :
                    new DefaultHandshakeHandler(new TomcatRequestUpgradeStrategy());
            registry.addEndpoint("/stomp").setHandshakeHandler(handler).withSockJS()
                    .setStreamBytesLimit(512 * 1024)
                    .setHttpMessageCacheSize(1000)
                    .setDisconnectDelay(30 * 1000);
        }

        @Override
        protected void configureMessageBroker(MessageBrokerRegistry registry) {
            registry.enableStompBrokerRelay("/topic/");
            registry.setApplicationDestinationPrefixes("/app");
        }

        @Override
        protected void configureClientOutboundChannel(ChannelRegistration registration) {
            registration.taskExecutor().corePoolSize(50);
        }

        @Override
        protected boolean configureMessageConverters(List<MessageConverter> messageConverters) {
            messageConverters.add(MESSAGE_CONVERTER);
            return false;
        }

        @Bean
        public HomeController homeController() {
            return new HomeController();
        }

        @Override
        public WebSocketMessageBrokerStats webSocketMessageBrokerStats() {
            WebSocketMessageBrokerStats stats = super.webSocketMessageBrokerStats();
            stats.setLoggingPeriod(5 * 1000);
            return stats;
        }
    }

    @RestController
    static class HomeController {
        public static final DateFormat DATE_FORMAT = SimpleDateFormat.getDateInstance();

        @RequestMapping(value = "/home", method = RequestMethod.GET)
        public void home() {
        }

        @MessageMapping("/greeting")
        public String handleGeeting(String greeting) {
            return "[" + DATE_FORMAT.format(new Date()) + "]" + greeting;
        }
    }
}
