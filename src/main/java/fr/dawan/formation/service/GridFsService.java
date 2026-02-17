package fr.dawan.formation.service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import org.bson.Document;
import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.types.ObjectId;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

import static com.mongodb.client.model.Filters.eq;

public class GridFsService {
  private final GridFSBucket bucket;
  private final MongoCollection<Document> vehicles;

  public GridFsService(MongoDatabase db) {
    this.bucket = GridFSBuckets.create(db);
    this.vehicles = db.getCollection("vehicles");
  }

  public ObjectId uploadAndAttach(String vehicleId, int incidentIndex, InputStream content, String filename) {
    ObjectId fileId = bucket.uploadFromStream(filename, content);
    ObjectId vId = new ObjectId(vehicleId);
    String path = "incidents." + incidentIndex + ".evidenceId";
    vehicles.updateOne(eq("_id", vId), new Document("$set", new Document(path, fileId).append("updatedAt", new Date())));
    return fileId;
  }

  public Path getIncidentEvidence(String vehicleId, int incidentIndex) throws Exception {
    ObjectId vId = new ObjectId(vehicleId);
    Document vehicle = vehicles.find(eq("_id", vId)).first();
    if (vehicle == null) throw new IllegalArgumentException("Vehicle not found: " + vehicleId);

    Object incidentsObj = vehicle.get("incidents");
    if (!(incidentsObj instanceof java.util.List)) throw new IllegalArgumentException("No incidents for vehicle");
    java.util.List<?> incs = (java.util.List<?>) incidentsObj;
    if (incidentIndex < 0 || incidentIndex >= incs.size()) throw new IndexOutOfBoundsException("Invalid incident index");

    Object incObj = incs.get(incidentIndex);
    if (!(incObj instanceof Document)) throw new IllegalStateException("Incident structure unexpected");
    Document inc = (Document) incObj;

    Object evidence = inc.get("evidenceId");
    if (!(evidence instanceof ObjectId)) throw new IllegalArgumentException("No evidence attached to incident");
    ObjectId fileId = (ObjectId) evidence;

    GridFSFile fileDoc = bucket.find(eq("_id", fileId)).first();
    String filename = (fileDoc != null) ? fileDoc.getFilename() : ("evidence_" + vehicleId + "_" + incidentIndex);

    Path out = Paths.get(filename + "_" + System.currentTimeMillis());
    try (OutputStream os = new FileOutputStream(out.toFile())) {
      bucket.downloadToStream(fileId, os);
    }
    return out;
  }
}
