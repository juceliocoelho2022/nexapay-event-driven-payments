package br.com.nexapay.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "permissions")
public class Permission {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 150)
    private String name;

    protected Permission() {
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
