package com.phuongheh.samples.portfolio.web.standalone;

import org.springframework.messaging.Message;
import org.springframework.messaging.support.AbstractSubscribableChannel;

import java.util.ArrayList;
import java.util.List;

public class TestMessageChannel extends AbstractSubscribableChannel {
    private final List<Message<?>> messages = new ArrayList<>();

    public List<Message<?>> getMessages() {
        return messages;
    }

    @Override
    protected boolean sendInternal(Message<?> message, long l) {
        this.messages.add(message);
        return true;
    }
}
