package fr.dawan.formation.model;

import java.util.Date;

public class Telemetry {
  private double lat;
  private double lon;
  private Date ts;
  private int batteryPercent;

  public Telemetry() {
  }

  public Telemetry(double lat, double lon, Date ts, int batteryPercent) {
      this.lat = lat;
      this.lon = lon;
      this.ts = ts;
      this.batteryPercent = batteryPercent;
  }

  public double getLat() { return lat; }
  public void setLat(double lat) { this.lat = lat; }

  public double getLon() { return lon; }
  public void setLon(double lon) { this.lon = lon; }

  public Date getTs() { return ts; }
  public void setTs(Date ts) { this.ts = ts; }
  
  public int getBatteryPercent() { return batteryPercent; }
  public void setBatteryPercent(int batteryPercent) { this.batteryPercent = batteryPercent; }
}