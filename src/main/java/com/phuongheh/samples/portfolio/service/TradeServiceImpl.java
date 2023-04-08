package com.phuongheh.samples.portfolio.service;

import com.phuongheh.samples.portfolio.Portfolio;
import com.phuongheh.samples.portfolio.PortfolioPosition;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class TradeServiceImpl implements TradeService {
    private static final Log logger = LogFactory.getLog(TradeServiceImpl.class);
    private final SimpMessageSendingOperations messageTemplate;
    private final PortfolioService portfolioService;
    private final List<TradeResult> tradeResults = new CopyOnWriteArrayList<>();

    @Autowired
    public TradeServiceImpl(SimpMessageSendingOperations messageTemplate, PortfolioService portfolioService) {
        this.messageTemplate = messageTemplate;
        this.portfolioService = portfolioService;
    }

    @Override
    public void executeTrade(Trade trade) {
        Portfolio portfolio = this.portfolioService.findPortfolio(trade.getUsername());
        String ticker = trade.getTicker();
        int sharesToTrade = trade.getShares();

        PortfolioPosition newPosition = (trade.getAction() == Trade.TradeAction.Buy) ? portfolio.buy(ticker, sharesToTrade) : portfolio.sell(ticker, sharesToTrade);
        if (newPosition == null) {
            String payload = "Rejected trade " + trade;
            this.messageTemplate.convertAndSendToUser(trade.getUsername(), "/queue/errors", payload);
            return;
        }
        this.tradeResults.add(new TradeResult(trade.getUsername(), newPosition));
    }

    @Scheduled(fixedDelay = 1500)
    public void sendTradeNotifications() {
        Map<String, Object> map = new HashMap<>();
        map.put(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON);
        for (TradeResult result : this.tradeResults) {
            if (System.currentTimeMillis() >= (result.timestamp + 1500)) {
                logger.debug("Sending position update: " + result.position);
                this.messageTemplate.convertAndSendToUser(result.user, "/queue/position-updates", result.position, map);
                this.tradeResults.remove(result);
            }
        }
    }

    private static class TradeResult {
        private final String user;
        private final PortfolioPosition position;
        private final long timestamp;

        public TradeResult(String user, PortfolioPosition position) {
            this.user = user;
            this.position = position;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
