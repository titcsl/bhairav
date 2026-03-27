package space.whatsgoinon.bhairav.entity;

import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
@Getter
@Setter
@Transactional
@Entity
@Table(name = "encrypted_records")
public class Encrypt {
    @Id
    private String id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String ciphertext;

    @Column(name = "enc_key", nullable = false)
    private byte[] encKey;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

}