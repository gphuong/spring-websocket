package com.phuongheh.samples.portfolio.service;

import java.math.BigDecimal;

public class Quote {
    private String ticker;
    private BigDecimal price;

    public Quote(String ticker, BigDecimal price) {
        this.ticker = ticker;
        this.price = price;
    }

    public Quote() {
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    @Override
    public String toString() {
        return "Quote{" +
                "ticker='" + ticker + '\'' +
                ", price=" + price +
                '}';
    }
}
