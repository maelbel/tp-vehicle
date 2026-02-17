package fr.dawan.formation.service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import fr.dawan.formation.model.Incident;
import fr.dawan.formation.model.Telemetry;
import fr.dawan.formation.model.Vehicle;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;

public class VehicleService {
    private final MongoCollection<Document> collection;

    public VehicleService(MongoDatabase db) {
      this.collection = db.getCollection("vehicles");
    }

    public void registerVehicle(Vehicle vehicle) {
      if (vehicle.getId() == null) vehicle.setId(new ObjectId());

      Date now = new Date();
      if (vehicle.getCreatedAt() == null) vehicle.setCreatedAt(now);
      vehicle.setUpdatedAt(now);

      Document document = toDocument(vehicle);

      collection.insertOne(document);
    }

    public void updateTelemetry(ObjectId vehicleId, Telemetry telemetry) {
      Document lastPos = new Document("lat", telemetry.getLat()).append("lon", telemetry.getLon()).append("ts", telemetry.getTs());

      Document set = new Document()
        .append("telemetry.lastPosition", lastPos)
        .append("telemetry.batteryPercent", telemetry.getBatteryPercent())
        .append("updatedAt", new Date());

      collection.updateOne(eq("_id", vehicleId), new Document("$set", set));
    }

    public void reportIncident(ObjectId vehicleId, Incident incident) {
      Document inc = new Document("date", incident.getDate())
        .append("type", incident.getType())
        .append("description", incident.getDescription());

      Document update = new Document("$push", new Document("incidents", inc))
        .append("$set", new Document("updatedAt", new Date()));

      collection.updateOne(eq("_id", vehicleId), update);
    }

    public List<Vehicle> findLowBatteryAndManyIncidents() {
      Document batteryCond = new Document("telemetry.batteryPercent", new Document("$lt", 20));
      Document sizeExpr = new Document("$gt", Arrays.asList(
        new Document("$size", new Document("$ifNull", Arrays.asList("$incidents", new ArrayList<>()))),
        2
      ));
      Document expr = new Document("$expr", sizeExpr);
      Document filter = new Document("$and", Arrays.asList(batteryCond, expr));

      List<Vehicle> result = new ArrayList<>();

      for (Document document : collection.find(filter)) {
          result.add(fromDocument(document));
      }

      return result;
    }

    private Document toDocument(Vehicle vehicle) {
      Document document = new Document();
      if (vehicle.getId() != null) document.append("_id", vehicle.getId());

      document.append("brand", vehicle.getBrand())
        .append("model", vehicle.getModel())
        .append("registration", vehicle.getRegistration())
        .append("ownerId", vehicle.getOwnerId());

      if (vehicle.getTelemetry() != null) {
        Document tel = new Document("lastPosition",
          new Document("lat", vehicle.getTelemetry().getLat()).append("lon", vehicle.getTelemetry().getLon()).append("ts", vehicle.getTelemetry().getTs()))
          .append("batteryPercent", vehicle.getTelemetry().getBatteryPercent());
        document.append("telemetry", tel);
      }

      if (vehicle.getIncidents() != null) {
        List<Document> list = new ArrayList<>();

        for (Incident inc : vehicle.getIncidents()) {
          Document idoc = new Document("date", inc.getDate()).append("type", inc.getType()).append("description", inc.getDescription());
          if (inc.getEvidenceId() != null) idoc.append("evidenceId", inc.getEvidenceId());
          list.add(idoc);
        }

        document.append("incidents", list);
      }

      if (vehicle.getSpecs() != null) document.append("specs", new Document(vehicle.getSpecs()));

      document.append("createdAt", vehicle.getCreatedAt()).append("updatedAt", vehicle.getUpdatedAt());

      return document;
    }

    private Vehicle fromDocument(Document document) {
      Vehicle vehicle = new Vehicle();
      vehicle.setId(document.getObjectId("_id"));
      vehicle.setBrand(document.getString("brand"));
      vehicle.setModel(document.getString("model"));
      vehicle.setRegistration(document.getString("registration"));
      vehicle.setOwnerId(document.getObjectId("ownerId"));

      Document telemetry = document.get("telemetry", Document.class);
      if (telemetry != null) {
        Document lp = telemetry.get("lastPosition", Document.class);
        Telemetry t = new Telemetry();

        if (lp != null) {
          Number lat = (Number) lp.get("lat");
          Number lon = (Number) lp.get("lon");
          t.setLat(lat != null ? lat.doubleValue() : 0.0);
          t.setLon(lon != null ? lon.doubleValue() : 0.0);
          t.setTs(lp.getDate("ts"));
        }

        Number bp = (Number) telemetry.get("batteryPercent");

        if (bp != null) t.setBatteryPercent(bp.intValue());
        
        vehicle.setTelemetry(t);
      }

      List<Document> incs = document.get("incidents", List.class);
      if (incs != null) {
          List<Incident> list = new ArrayList<>();
          
            for (Document idoc : incs) {
              Object idEvidence = idoc.get("evidenceId");
              ObjectId evidenceId = null;
              if (idEvidence instanceof ObjectId) evidenceId = (ObjectId) idEvidence;
              list.add(new Incident(idoc.getDate("date"), idoc.getString("type"), idoc.getString("description"), evidenceId));
            }

          vehicle.setIncidents(list);
      }

      Document specs = document.get("specs", Document.class);

      if (specs != null) vehicle.setSpecs(specs);

      vehicle.setCreatedAt(document.getDate("createdAt"));
      vehicle.setUpdatedAt(document.getDate("updatedAt"));
      
      return vehicle;
    }
}
