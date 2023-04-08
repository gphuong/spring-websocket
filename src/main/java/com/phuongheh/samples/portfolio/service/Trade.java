package com.phuongheh.samples.portfolio.service;

import com.sun.org.apache.xpath.internal.objects.XString;

public class Trade {
    private String ticker;
    private int shares;
    private TradeAction action;
    private String username;

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public int getShares() {
        return shares;
    }

    public void setShares(int shares) {
        this.shares = shares;
    }

    public TradeAction getAction() {
        return action;
    }

    public void setAction(TradeAction action) {
        this.action = action;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public String toString() {
        return "Trade{" +
                "ticker='" + ticker + '\'' +
                ", shares=" + shares +
                ", action=" + action +
                ", username='" + username + '\'' +
                '}';
    }

    public enum TradeAction {
        Buy, Sell;
    }
}
