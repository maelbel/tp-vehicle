package fr.dawan.formation.service;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FleetAnalytics {
  private final MongoCollection<Document> vehicles;

  public FleetAnalytics(MongoDatabase db) {
    this.vehicles = db.getCollection("vehicles");
  }

  public List<Document> batteryAverageByBrand() {
    List<Document> pipeline = Arrays.asList(
      new Document("$match", new Document("telemetry.batteryPercent", new Document("$exists", true))),
      new Document("$group", new Document("_id", "$brand")
        .append("avgBattery", new Document("$avg", "$telemetry.batteryPercent"))) ,
      new Document("$project", new Document("brand", "$_id").append("avgBattery", 1).append("_id", 0))
    );

    AggregateIterable<Document> it = vehicles.aggregate(pipeline);
    List<Document> out = new ArrayList<>();
    for (Document document : it) out.add(document);

    return out;
  }

  public List<Document> maintenanceAlertsEngineIncidents() {
    List<Document> pipeline = Arrays.asList(
      new Document("$unwind", "$incidents"),
      new Document("$match", new Document("incidents.type", "Moteur")),
      new Document("$lookup", new Document("from", "users").append("localField", "ownerId").append("foreignField", "_id").append("as", "owner")),
      new Document("$unwind", new Document("path", "$owner").append("preserveNullAndEmptyArrays", true)),
      new Document("$project", new Document("registration", 1)
        .append("brand", 1)
        .append("model", 1)
        .append("incident", "$incidents")
        .append("ownerName", "$owner.name"))
    );

    AggregateIterable<Document> it = vehicles.aggregate(pipeline);
    List<Document> out = new ArrayList<>();
    for (Document d : it) out.add(d);

    return out;
  }

  public List<Document> topOwners() {
    List<Document> pipeline = Arrays.asList(
      new Document("$group", new Document("_id", "$ownerId").append("vehicleCount", new Document("$sum", 1))),
      new Document("$sort", new Document("vehicleCount", -1)),
      new Document("$limit", 3),
      new Document("$lookup", new Document("from", "users").append("localField", "_id").append("foreignField", "_id").append("as", "owner")),
      new Document("$unwind", new Document("path", "$owner").append("preserveNullAndEmptyArrays", true)),
      new Document("$project", new Document("ownerId", "$_id").append("ownerName", "$owner.name").append("vehicleCount", 1).append("_id", 0))
    );

    AggregateIterable<Document> it = vehicles.aggregate(pipeline);
    List<Document> out = new ArrayList<>();
    for (Document d : it) out.add(d);
    
    return out;
  }
}
