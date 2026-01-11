package net.jackiemclean.mza;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupStateRepository extends JpaRepository<GroupState, String> {
    Optional<GroupState> findByName(String name);

    Optional<GroupState> findByZonesContaining(String zoneName);

    boolean existsByName(String name);

    void deleteByZonesContaining(String zoneName);
}
