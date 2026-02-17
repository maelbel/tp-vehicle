package fr.dawan.formation.model;

import java.util.Date;
import org.bson.types.ObjectId;

public class Incident {
  private Date date;
  private String type;
  private String description;
  private ObjectId evidenceId;

  public Incident() {
  }

  public Incident(Date date, String type, String description) {
    this(date, type, description, null);
  }

  public Incident(Date date, String type, String description, ObjectId evidenceId) {
    this.date = date;
    this.type = type;
    this.description = description;
    this.evidenceId = evidenceId;
  }

  public Date getDate() { return date; }
  public void setDate(Date date) { this.date = date; }

  public String getType() { return type; }
  public void setType(String type) { this.type = type; }

  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }

  public ObjectId getEvidenceId() { return evidenceId; }
  public void setEvidenceId(ObjectId evidenceId) { this.evidenceId = evidenceId; }
}
