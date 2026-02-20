package fr.dawan.formation.reactive;

import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import org.bson.Document;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mongodb.client.result.InsertManyResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class ReactiveTelemetryApp {

    private static final Logger logger = LoggerFactory.getLogger(ReactiveTelemetryApp.class);

    static class CollectSubscriber<T> implements Subscriber<T> {
        private final List<T> items = new ArrayList<>();
        private final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void onSubscribe(Subscription s) {
            s.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(T t) {
            items.add(t);
        }

        @Override
        public void onError(Throwable t) {
            logger.error("Reactive subscriber error", t);
            latch.countDown();
        }

        @Override
        public void onComplete() {
            latch.countDown();
        }

        public List<T> getItems() {
            return items;
        }

        public boolean await(long timeoutSec) throws InterruptedException {
            return latch.await(timeoutSec, TimeUnit.SECONDS);
        }
    }

    static class SingleSubscriber<T> implements Subscriber<T> {
        private final CountDownLatch latch = new CountDownLatch(1);
        private T item;

        @Override
        public void onSubscribe(Subscription s) { s.request(1); }

        @Override
        public void onNext(T t) { this.item = t; }

        @Override
        public void onError(Throwable t) { logger.error("Reactive single subscriber error", t); latch.countDown(); }

        @Override
        public void onComplete() { latch.countDown(); }

        public T getItem() { return item; }

        public boolean await(long timeoutSec) throws InterruptedException { return latch.await(timeoutSec, TimeUnit.SECONDS); }
    }

    public static void main(String[] args) throws Exception {
        String uri = "mongodb://localhost:27017";
        try (MongoClient client = MongoClients.create(uri)) {
            MongoDatabase db = client.getDatabase("fleetdb");
            MongoCollection<Document> coll = db.getCollection("telemetry_history");

            final int total = 100_000;
            final int batch = 1_000;
            logger.info("Starting reactive insertion of {} documents", total);
            insertReactive(coll, total, batch);

            SingleSubscriber<Document> sampleSub = new SingleSubscriber<>();
            coll.find().projection(new Document(FIELD_VEHICLE_ID, 1)).limit(1).subscribe(sampleSub);
            boolean waited = sampleSub.await(10);
            if (!waited) logger.warn("Timeout waiting for sample vehicleId");
            Document sample = sampleSub.getItem();
            if (sample == null) {
                logger.warn("No sample vehicleId found");
                return;
            }
            String vehicleId = sample.getString(FIELD_VEHICLE_ID);

            Document filter = new Document(FIELD_VEHICLE_ID, vehicleId).append(FIELD_TIMESTAMP, new Document("$gte", java.util.Date.from(Instant.now().minusSeconds(30L*24*3600))));

            logger.info("=== Explain BEFORE index (vehicleId + timestamp) ===");
            Document explainBefore = explainFind(coll, filter);
            if (explainBefore != null) logger.info(explainBefore.toJson());

            logger.info("=== Explain BEFORE index (timestamp only) ===");
            Document explainBeforeTs = explainFind(coll, new Document(FIELD_TIMESTAMP, new Document("$gte", java.util.Date.from(Instant.now().minusSeconds(30L*24*3600)))));
            if (explainBeforeTs != null) logger.info(explainBeforeTs.toJson());

            logger.info("Creating compound index { vehicleId:1, timestamp:-1 }");
            CollectSubscriber<String> idxSub = new CollectSubscriber<>();
            coll.createIndex(Indexes.compoundIndex(Indexes.ascending(FIELD_VEHICLE_ID), Indexes.descending(FIELD_TIMESTAMP)), new IndexOptions()).subscribe(idxSub);
            idxSub.await(10);

            logger.info("=== Explain AFTER index (vehicleId + timestamp) ===");
            Document explainAfter = explainFind(coll, filter);
            if (explainAfter != null) logger.info(explainAfter.toJson());

            logger.info("=== Explain AFTER index (timestamp only) ===");
            Document explainAfterTs = explainFind(coll, new Document(FIELD_TIMESTAMP, new Document("$gte", java.util.Date.from(Instant.now().minusSeconds(30L*24*3600)))));
            if (explainAfterTs != null) logger.info(explainAfterTs.toJson());

            logger.info("=== Aggregation: total energy per city (last 30 days) ===");
            Document match = new Document("$match", new Document(FIELD_TIMESTAMP, new Document("$gte", java.util.Date.from(Instant.now().minusSeconds(30L*24*3600)))));
            Document group = new Document("$group", new Document("_id", "$location.city").append("totalEnergy", new Document("$sum", "$energyConsumed")).append("avgEnergy", new Document("$avg", "$energyConsumed")).append("count", new Document("$sum", 1)));
            Document sort = new Document("$sort", new Document("totalEnergy", -1));
            CollectSubscriber<Document> aggSub = new CollectSubscriber<>();
            coll.aggregate(List.of(match, group, sort)).subscribe(aggSub);
            boolean aggWaited = aggSub.await(30);
            if (!aggWaited) logger.warn("Aggregation timed out");
            aggSub.getItems().forEach(d -> logger.info(d.toJson()));

            logger.info("Done.");
        }
    }

    private static final Random RANDOM = new Random();

    private static void insertReactive(MongoCollection<Document> coll, int total, int batchSize) throws InterruptedException {
        String[] cities = new String[]{"Paris","Lyon","Marseille","Toulouse","Nice","Nantes","Strasbourg","Bordeaux","Lille","Rennes"};
        List<String> vehicles = new ArrayList<>();
        for (int i = 0; i < 200; i++) vehicles.add(String.format("VEH-%04d", i+1));

        int inserted = 0;
        while (inserted < total) {
            int toAdd = Math.min(batchSize, total - inserted);
            List<Document> batch = new ArrayList<>(toAdd);
            for (int i = 0; i < toAdd; i++) {
                String city = cities[RANDOM.nextInt(cities.length)];
                String vehicle = vehicles.get(RANDOM.nextInt(vehicles.size()));
                long offset = ThreadLocalRandom.current().nextLong(0, 90L * 24 * 3600 * 1000);
                Document doc = new Document(FIELD_VEHICLE_ID, vehicle)
                        .append(FIELD_TIMESTAMP, new java.util.Date(System.currentTimeMillis() - offset))
                        .append("location", new Document("city", city).append("coords", List.of(-1.0 + RANDOM.nextDouble()*4.0, 43.0 + RANDOM.nextDouble()*6.0)))
                        .append("energyConsumed", Math.round((0.1 + RANDOM.nextDouble()*9.9)*1000.0)/1000.0)
                        .append("speed", Math.round(RANDOM.nextDouble()*130.0*100.0)/100.0)
                        .append("createdAt", new java.util.Date());
                batch.add(doc);
            }
            CountDownLatch latch = new CountDownLatch(1);
            coll.insertMany(batch).subscribe(new Subscriber<InsertManyResult>(){
                @Override public void onSubscribe(Subscription s) { s.request(1); }
                @Override public void onNext(InsertManyResult result) {
                    // intentionally left blank: insertMany result is not used here
                }
                @Override public void onError(Throwable t) { logger.error("InsertMany error", t); latch.countDown(); }
                @Override public void onComplete() { latch.countDown(); }
            });
            boolean ok = latch.await(60, TimeUnit.SECONDS);
            if (!ok) logger.warn("InsertMany batch timed out");
            inserted += toAdd;
            if (inserted % (batchSize*5) == 0) logger.info("{} documents inserted...", inserted);
        }
        logger.info("Inserted total: {}", inserted);
    }

    private static Document explainFind(MongoCollection<Document> coll, Document filter) throws InterruptedException {
        SingleSubscriber<Document> sub = new SingleSubscriber<>();
        coll.find(filter).sort(Sorts.descending(FIELD_TIMESTAMP)).limit(100).explain().subscribe(sub);
        boolean waited = sub.await(20);
        if (!waited) {
            logger.warn("Timeout waiting for explain");
            return null;
        }
        return sub.getItem();
    }

    private static final String FIELD_VEHICLE_ID = "vehicleId";
    private static final String FIELD_TIMESTAMP = "timestamp";
}
