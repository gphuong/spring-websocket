package com.phuongheh.samples.portfolio.service;

import com.phuongheh.samples.portfolio.Portfolio;
import com.phuongheh.samples.portfolio.PortfolioPosition;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class PortfolioServiceImpl implements PortfolioService {
    private final Map<String, Portfolio> portfolioLookup = new HashMap<>();

    public PortfolioServiceImpl() {
        Portfolio portfolio = new Portfolio();
        portfolio.addPosition(new PortfolioPosition("Citrix Systems, Inc", "CTXS", 24.30, 75));
        portfolio.addPosition(new PortfolioPosition("Dell Inc.", "DELL", 13.44, 50));
        portfolio.addPosition(new PortfolioPosition("Microsoft", "MSFT", 34.15, 33));
        portfolio.addPosition(new PortfolioPosition("Oracle", "ORCL", 31.22, 45));
        this.portfolioLookup.put("fabrice", portfolio);

        portfolio = new Portfolio();
        portfolio.addPosition(new PortfolioPosition("EMC Corporation", "EMC", 24.30, 75));
        portfolio.addPosition(new PortfolioPosition("Google Inc", "GOOG", 905.09, 5));
        portfolio.addPosition(new PortfolioPosition("VMWare, Inc.", "VMW", 65.58, 23));
        portfolio.addPosition(new PortfolioPosition("Red Hat", "RHT", 48.30, 15));
        this.portfolioLookup.put("paulson", portfolio);
    }

    @Override
    public Portfolio findPortfolio(String username) {
        Portfolio portfolio = this.portfolioLookup.get(username);
        if (portfolio == null) {
            throw new IllegalArgumentException(username);
        }
        return portfolio;
    }
}
