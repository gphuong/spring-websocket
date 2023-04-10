package com.phuongheh.samples.portfolio.web.support;

import org.junit.Test;

import java.security.Principal;

public class TestPrincipal implements Principal {

    private final String name;

    public TestPrincipal(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return null;
    }
}
