// Create vehicles collection with JSON Schema validator and indexes
const validator = {
  $jsonSchema: {
    bsonType: "object",
    required: ["make","model","registration","ownerId","telemetry"],
    properties: {
      make: { bsonType: "string" },
      model: { bsonType: "string" },
      registration: { bsonType: "string" },
      ownerId: { bsonType: "objectId" },
      telemetry: {
        bsonType: "object",
        required: ["lastPosition","batteryPercent"],
        properties: {
          lastPosition: {
            bsonType: "object",
            required: ["lat","lon","ts"],
            properties: {
              lat: { bsonType: "double" },
              lon: { bsonType: "double" },
              ts: { bsonType: "date" }
            }
          },
          batteryPercent: { bsonType: "int" }
        }
      },
      incidents: {
        bsonType: "array",
        items: {
          bsonType: "object",
          required: ["date","type"],
          properties: {
            date: { bsonType: "date" },
            type: { bsonType: "string" },
            description: { bsonType: "string" }
          }
        }
      },
      specs: { bsonType: "object" },
      modelType: { bsonType: "string" },
      createdAt: { bsonType: "date" },
      updatedAt: { bsonType: "date" }
    }
  }
};

db.createCollection("vehicles", { validator: validator, validationLevel: "moderate" });
db.vehicles.createIndex({ registration: 1 }, { unique: true });
db.vehicles.createIndex({ ownerId: 1 });
// If using GeoJSON for lastPosition, create 2dsphere index on telemetry.location
// db.vehicles.createIndex({ "telemetry.lastPosition": "2dsphere" });

// Example partial index for a spec frequently queried (range_km)
db.vehicles.createIndex({ "specs.range_km": 1 }, { partialFilterExpression: { "specs.range_km": { $exists: true } } });

print('vehicles collection created/validated with indexes');
