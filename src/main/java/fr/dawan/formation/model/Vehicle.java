package fr.dawan.formation.model;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;

public class Vehicle {
  private ObjectId id;
  private String brand;
  private String model;
  private String registration;
  private ObjectId ownerId;
  private Telemetry telemetry;
  private List<Incident> incidents;
  private Map<String, Object> specs;
  private Date createdAt;
  private Date updatedAt;

  public ObjectId getId() { return id; }
  public void setId(ObjectId id) { this.id = id; }

  public String getBrand() { return brand; }
  public void setBrand(String brand) { this.brand = brand; }

  public String getModel() { return model; }
  public void setModel(String model) { this.model = model; }

  public String getRegistration() { return registration; }
  public void setRegistration(String registration) { this.registration = registration; }

  public ObjectId getOwnerId() { return ownerId; }
  public void setOwnerId(ObjectId ownerId) { this.ownerId = ownerId; }

  public Telemetry getTelemetry() { return telemetry; }
  public void setTelemetry(Telemetry telemetry) { this.telemetry = telemetry; }

  public List<Incident> getIncidents() { return incidents; }
  public void setIncidents(List<Incident> incidents) { this.incidents = incidents; }

  public Map<String, Object> getSpecs() { return specs; }
  public void setSpecs(Map<String, Object> specs) { this.specs = specs; }

  public Date getCreatedAt() { return createdAt; }
  public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

  public Date getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
