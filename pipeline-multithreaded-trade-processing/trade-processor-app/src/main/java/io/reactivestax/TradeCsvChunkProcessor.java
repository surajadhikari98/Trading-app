package io.reactivestax;

import io.reactivestax.hikari.DataSource;

import java.io.*;
import java.sql.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class TradeCsvChunkProcessor implements ChunkProcessor {

    static Connection connection;
    int numberOfChunks;
    ExecutorService chunkProcessorThreadPool;
    static ConcurrentHashMap<String, Integer> queueDistributorMap = new ConcurrentHashMap();
    static LinkedBlockingDeque<String> queue1 = new LinkedBlockingDeque<>();
    static LinkedBlockingDeque<String> queue2 = new LinkedBlockingDeque<>();
    static LinkedBlockingDeque<String> queue3 = new LinkedBlockingDeque<>();
    static AtomicInteger currentQueueIndex = new AtomicInteger(0);

    static {
        try {
            connection = DataSource.getConnection();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public TradeCsvChunkProcessor(ExecutorService chunkProcessorThreadPool, int numberOfChunks) {
        this.chunkProcessorThreadPool = chunkProcessorThreadPool;
        this.numberOfChunks = numberOfChunks;
    }

    public void processChunks() {
        try {
            for (int i = 1; i <= numberOfChunks; i++) {
                String chunkFileName = "trades_chunk_" + i + ".csv";
                chunkProcessorThreadPool.submit(() -> {
                    try {
                        insertIntoTradePayload(chunkFileName);
                    } catch (IOException | SQLException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            System.out.println("queue1 size" + queue1.size());
            System.out.println("queue2 size" + queue2.size());
            System.out.println("queue3 size" + queue3.size());
            System.out.println("Map size" + queueDistributorMap.size());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void insertIntoTradePayload(String fileName) throws SQLException, IOException, InterruptedException {
        String filePath = "/Users/Suraj.Adhikari/sources/student-mode-programs/suad-bootcamp-2024/pipeline-multithreaded-trade-processing/trade-processor-app/src/main/java/io/reactivestax/files/";
        String insertQuery = "INSERT INTO trade_payloads (trade_id, status, status_reason, payload) VALUES (?, ?, ?,?)";
        PreparedStatement statement = connection.prepareStatement(insertQuery);
        String line;
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath + fileName))) {
            reader.readLine();
            while ((line = reader.readLine()) != null) {
                String[] split = line.split(",");
                statement.setString(1, split[0]);
                statement.setString(2, checkValidity(split) ? "valid" : "inValid");
                statement.setString(3, checkValidity(split) ? "All field present " : "Fields missing");
                statement.setString(4, line);
                statement.executeUpdate();
                writeToTradeQueue(split);
            }
        }
    }

    @Override
    public void writeToTradeQueue(String[] trade) throws InterruptedException {
        // Get the queue number, or assign one in a round-robin manner if not already assigned
        int queueNumber = queueDistributorMap.computeIfAbsent(trade[2], k -> (currentQueueIndex.incrementAndGet() % 3) + 1); //generate 1,2,3
        selectQueue(trade[0], queueNumber);
        System.out.println("Assigned trade ID: " + trade[0] + " to queue: " + queueNumber);
    }

    private static void selectQueue(String tradeId, Integer queueNumber) throws InterruptedException {
        switch (queueNumber) {
            case 1:
                queue1.put(tradeId);
                break;
            case 2:
                queue2.put(tradeId);
                break;
            case 3:
                queue3.put(tradeId);
                break;
        }
    }

    private static boolean checkValidity(String[] split) {
        return (split[0]) != null;
    }

    public void startMultiThreadsForReadingFromQueue(ExecutorService executorService) throws Exception {
        executorService.submit(new CsvTradeProcessor(queue1));
        executorService.submit(new CsvTradeProcessor(queue2));
        executorService.submit(new CsvTradeProcessor(queue3));
        executorService.shutdown();
    }
}
