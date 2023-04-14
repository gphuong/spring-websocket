package com.phuongheh.samples.portfolio.web.support;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.websocket.server.WsContextListener;
import org.springframework.web.SpringServletContainerInitializer;
import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

public class TomcatWebSocketTestServer implements WebSocketTestServer {
    private final Tomcat tomcatServer;
    private final int port;
    private Context context;

    public TomcatWebSocketTestServer(int port) {
        this.port = port;
        Connector connector = new Connector(Http11NioProtocol.class.getName());
        connector.setPort(this.port);

        File baseDir = createTempDir("tomcat");
        String baseDirPath = baseDir.getAbsolutePath();

        this.tomcatServer = new Tomcat();
        this.tomcatServer.setBaseDir(baseDirPath);
        this.tomcatServer.setPort(this.port);
        this.tomcatServer.getService().addConnector(connector);
        this.tomcatServer.setConnector(connector);
    }

    private File createTempDir(String prefix) {
        try {
            File tempFolder = File.createTempFile(prefix + ".", "." + getPort());
            tempFolder.delete();
            tempFolder.mkdir();
            tempFolder.deleteOnExit();
            return tempFolder;
        } catch (IOException ex) {
            throw new RuntimeException("Unable to create temp directory", ex);
        }

    }

    @Override
    public int getPort() {
        return this.port;
    }

    @Override
    public void deployConfig(WebApplicationContext cxt) {
        this.context = this.tomcatServer.addContext("", System.getProperty("java.io.tmpdir"));
        Tomcat.addServlet(context, "dispatcherServlet", new DispatcherServlet(cxt));
        this.context.addServletMappingDecoded("/", "dispatcherServlet");
    }

    public void deployConfig(Class<? extends WebApplicationInitializer>... initializer) {
        this.context = this.tomcatServer.addContext("", System.getProperty("java.io.tmpdir"));
        Wrapper defaultServlet = this.context.createWrapper();
        defaultServlet.setName("default");
        defaultServlet.setServletClass("org.apache.catalina.servlets.DefaultServlet");
        this.context.addApplicationListener(WsContextListener.class.getName());
        this.context.addServletContainerInitializer(new SpringServletContainerInitializer(), new HashSet<Class<?>>(Arrays.asList(initializer)));
    }

    @Override
    public void undeployConfig() {
        if (this.context != null) {
            this.tomcatServer.getHost().removeChild(this.context);
        }
    }

    @Override
    public void start() throws Exception {
        this.tomcatServer.start();
    }

    @Override
    public void stop() throws Exception {
        this.tomcatServer.stop();
    }
}
