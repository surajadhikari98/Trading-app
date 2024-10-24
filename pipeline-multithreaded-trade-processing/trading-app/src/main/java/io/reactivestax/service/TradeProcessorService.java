package io.reactivestax.service;

import io.reactivestax.contract.QueueSetup;
import io.reactivestax.contract.TradeProcessor;
import io.reactivestax.contract.repository.JournalEntryRepository;
import io.reactivestax.contract.repository.PositionRepository;
import io.reactivestax.contract.repository.SecuritiesReferenceRepository;
import io.reactivestax.model.Trade;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static io.reactivestax.factory.BeanFactory.*;
import static io.reactivestax.utils.Utility.prepareTrade;

@Slf4j
public class TradeProcessorService implements Callable<Void>, TradeProcessor {
    private static final AtomicInteger countSec = new AtomicInteger(0);
    private final String queueName;

    public TradeProcessorService(String queueName) {
        this.queueName = queueName;
    }


    @Override
    public Void call() {
        try {
            processTrade();
        } catch (Exception e) {
            TradeProcessorService.log.info("trade processor:  {}", e.getMessage());
        }
        return null;
    }

    @Override
    public void processTrade() throws Exception {
        QueueSetup queueSetUp = getQueueSetUp();
        assert queueSetUp != null;
        queueSetUp.publishMessage(queueName);
    }

    public static void processJournalWithPosition(String tradeId) throws Exception {
        String payload = getTradePayloadRepository().readTradePayloadByTradeId(tradeId);
        SecuritiesReferenceRepository lookupSecuritiesRepository = getLookupSecuritiesRepository();
        JournalEntryRepository journalEntryRepository = getJournalEntryRepository();
        Trade trade = prepareTrade(payload);
        log.info("result journal{}", payload);
        if (!lookupSecuritiesRepository.lookupSecurities(trade.getCusip())) {
            log.warn("No security found....");
            log.debug("times {} {}", trade.getCusip(), countSec.incrementAndGet());
            throw new Exception(); // For checking the max retry mechanism throwing error and catching it in retry mechanism.....
        } else {
            journalEntryRepository.saveJournalEntry(trade);
            processPosition(trade);
        }
    }


    public static void processPosition(Trade trade) throws Exception {
        PositionRepository positionsRepository = getPositionsRepository();
        Integer version = positionsRepository.getCusipVersion(trade);
        if (version != null) {
            positionsRepository.updatePosition(trade, version);
        } else {
            positionsRepository.insertPosition(trade);
        }
    }
}
