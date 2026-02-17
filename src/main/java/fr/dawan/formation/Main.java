package fr.dawan.formation;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import fr.dawan.formation.model.Incident;
import fr.dawan.formation.model.Telemetry;
import fr.dawan.formation.model.User;
import fr.dawan.formation.model.Vehicle;
import fr.dawan.formation.service.FleetAnalytics;
import fr.dawan.formation.service.GridFsService;
import fr.dawan.formation.service.VehicleService;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        String uri = System.getenv().getOrDefault("MONGO_URI", "mongodb://localhost:27017");
        try (MongoClient client = MongoClients.create(uri)) {
            MongoDatabase db = client.getDatabase("fleetdb");

            db.getCollection("users").drop();
            db.getCollection("vehicles").drop();
            db.getCollection("fs.files").drop();
            db.getCollection("fs.chunks").drop();

            VehicleService vehicleService = new VehicleService(db);
            FleetAnalytics analytics = new FleetAnalytics(db);
            GridFsService gridFs = new GridFsService(db);

            User user = new User();
            user.setName("Jean Dupont");
            Date now = new Date();
            user.setCreatedAt(now);
            user.setUpdatedAt(now);

            org.bson.Document userDoc = new org.bson.Document()
                .append("name", user.getName())
                .append("createdAt", user.getCreatedAt())
                .append("updatedAt", user.getUpdatedAt());
            var usersCol = db.getCollection("users");
            usersCol.insertOne(userDoc);
            ObjectId userId = userDoc.getObjectId("_id");
            user.setId(userId);
            log.info("Created user {} with id={} ", user.getName(), userId);

            Vehicle vehicle = new Vehicle();
            vehicle.setBrand("Renault");
            vehicle.setModel("Zoe");
            vehicle.setRegistration("AB-123-CD");
            vehicle.setOwnerId(userId);

            Telemetry telemetry = new Telemetry(48.8566, 2.3522, new Date(), 78);
            vehicle.setTelemetry(telemetry);
            vehicleService.registerVehicle(vehicle);
            log.info("Registered vehicle {} (owner={})", vehicle.getRegistration(), user.getName());

            Telemetry updated = new Telemetry(48.8570, 2.3530, new Date(), 65);
            vehicleService.updateTelemetry(vehicle.getId(), updated);

            log.info("Updated telemetry for {} battery={}%%", vehicle.getRegistration(), updated.getBatteryPercent());

            Incident incident = new Incident(new Date(), "Moteur", "Strange noise from engine");
            vehicleService.reportIncident(vehicle.getId(), incident);

            log.info("Reported incident for {}: {}", vehicle.getRegistration(), incident.getType());

            try (FileInputStream fis = new FileInputStream("incident.jpg")) {
                ObjectId fileId = gridFs.uploadAndAttach(vehicle.getId().toHexString(), 0, fis, "incident.jpg");
                log.info("Uploaded evidence to GridFS with id={}", fileId);
            } catch (FileNotFoundException e) {
                log.warn("incident.jpg not found; skipping GridFS upload demo");
            }

            List<Document> avgByBrand = analytics.batteryAverageByBrand();
            log.info("Battery average by brand: {} results", avgByBrand.size());
            avgByBrand.forEach(d -> log.info("  {}", d.toJson()));

            List<Document> alerts = analytics.maintenanceAlertsEngineIncidents();
            log.info("Maintenance alerts (engine incidents): {} results", alerts.size());
            alerts.forEach(d -> log.info("  {}", d.toJson()));

            List<Document> topOwners = analytics.topOwners();
            log.info("Top owners: {} results", topOwners.size());
            topOwners.forEach(d -> log.info("  {}", d.toJson()));

            try {
                Path downloaded = gridFs.getIncidentEvidence(vehicle.getId().toHexString(), 0);
                log.info("Downloaded evidence to {}", downloaded.toAbsolutePath());
            } catch (Exception e) {
                log.warn("Could not download evidence: {}", e.getMessage());
            }
        }
    }
}