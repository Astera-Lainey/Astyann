package afb.astyann.authservice.repository;

import afb.astyann.authservice.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUserId(UUID userId);

    Optional<User> findByResetToken(String resetToken);

    boolean existsByEmail(String email);

}
