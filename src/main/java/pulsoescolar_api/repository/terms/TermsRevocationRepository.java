package pulsoescolar_api.repository.terms;

import org.springframework.data.jpa.repository.JpaRepository;
import pulsoescolar_api.entity.terms.TermsRevocation;

public interface TermsRevocationRepository extends JpaRepository<TermsRevocation, Long> {}
