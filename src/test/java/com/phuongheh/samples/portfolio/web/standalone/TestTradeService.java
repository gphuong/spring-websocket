package com.phuongheh.samples.portfolio.web.standalone;

import com.phuongheh.samples.portfolio.service.Trade;
import com.phuongheh.samples.portfolio.service.TradeService;

import java.util.ArrayList;
import java.util.List;

public class TestTradeService implements TradeService {
    private final List<Trade> trades = new ArrayList<>();

    public List<Trade> getTrades() {
        return trades;
    }

    @Override
    public void executeTrade(Trade trade) {
        this.trades.add(trade);
    }
}
