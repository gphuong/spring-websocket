package com.phuongheh.samples.portfolio.web.tomcat;

import com.phuongheh.samples.portfolio.config.DispatcherServletInitializer;
import com.phuongheh.samples.portfolio.config.WebConfig;
import com.phuongheh.samples.portfolio.web.support.TomcatWebSocketTestServer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.BeforeClass;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.SocketUtils;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.sockjs.client.SockJsClient;

public class IntegrationPortfolioTests {
    private static Log logger = LogFactory.getLog(IntegrationPortfolioTests.class);
    private static int port;
    private static TomcatWebSocketTestServer server;
    private static SockJsClient sockJsClient;
    private static final WebSocketHttpHeaders headers = new WebSocketHttpHeaders();

    @BeforeClass
    public static void setup(){
        System.setProperty("spring.profiles.active", "test.tomcat");
        port = SocketUtils.findAvailableTcpPort();
        server = new TomcatWebSocketTestServer(port);
        server.deployConfig(TestDis);
    }
    public static class TestDispatcherServletInitializer extends DispatcherServletInitializer{
        @Override
        protected Class<?>[] getServletConfigClasses() {
            return new Class[]{WebConfig.class, TestWebSocketConfig.class}
        }
    }

    @Configuration
    @EnableScheduling
    @ComponentScan(basePackages = "org.springframework.samples", excludeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION))
}
