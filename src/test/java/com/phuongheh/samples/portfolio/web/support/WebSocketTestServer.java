package com.phuongheh.samples.portfolio.web.support;

import org.springframework.web.context.WebApplicationContext;

public interface WebSocketTestServer {
    int getPort();

    void deployConfig(WebApplicationContext cxt);

    void undeployConfig();

    void start() throws Exception;

    void stop() throws Exception;
}
