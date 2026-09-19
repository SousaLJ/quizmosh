package io.quizmosh.application.account;
import io.quizmosh.domain.account.*;
import java.util.Optional;
public interface AccountRepository {
    record Resolution(User user, boolean created) {}
    Resolution resolve(UserIdentity identity);
    Optional<User> find(String id);
    void delete(String id);
}
