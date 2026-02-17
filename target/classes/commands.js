print('1) Battery average per brand');
db.vehicles.aggregate([
  { $match: { 'telemetry.batteryPercent': { $exists: true } } },
  { $group: { _id: '$brand', avgBattery: { $avg: '$telemetry.batteryPercent' } } },
  { $project: { _id: 0, brand: '$_id', avgBattery: 1 } }
]).forEach(doc => printjson(doc));

print('\n----------------------------------------------------\n');

print('2) Maintenance alerts (incidents.type == "Moteur")');
db.vehicles.aggregate([
  { $unwind: '$incidents' },
  { $match: { 'incidents.type': 'Moteur' } },
  { $lookup: { from: 'users', localField: 'ownerId', foreignField: '_id', as: 'owner' } },
  { $unwind: { path: '$owner', preserveNullAndEmptyArrays: true } },
  { $project: {
      registration: 1,
      brand: 1,
      model: 1,
      incident: '$incidents',
      ownerName: '$owner.name'
  } }
]).forEach(doc => printjson(doc));

print('\n----------------------------------------------------\n');

print('3) Top 3 owners by vehicle count');
db.vehicles.aggregate([
  { $group: { _id: '$ownerId', vehicleCount: { $sum: 1 } } },
  { $sort: { vehicleCount: -1 } },
  { $limit: 3 },
  { $lookup: { from: 'users', localField: '_id', foreignField: '_id', as: 'owner' } },
  { $unwind: { path: '$owner', preserveNullAndEmptyArrays: true } },
  { $project: { _id: 0, ownerId: '$_id', ownerName: '$owner.name', vehicleCount: 1 } }
]).forEach(doc => printjson(doc));

print('\nScript complete.');
