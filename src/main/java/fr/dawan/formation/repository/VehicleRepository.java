package fr.dawan.formation.repository;

import fr.dawan.formation.model.Vehicle;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import java.util.List;

@RepositoryRestResource(collectionResourceRel = "vehicles", path = "vehicles")
public interface VehicleRepository extends MongoRepository<Vehicle, ObjectId> {
    List<Vehicle> findByStatus(String status);
}
