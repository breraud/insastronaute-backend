package fr.insastronaute.webapi.department.domain;

import jakarta.persistence.*;
import java.util.UUID;


@Entity
@Table(name = "departments")
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "short_name", nullable = false, unique = true, length = 50)
    private String shortName;

    @Column(name = "keycloak_group_name", unique = true)
    private String keycloakGroupName;

    @Column(nullable = false, length = 7)
    private String color = "#6366F1";

    /**
     * Une audience, pas une filiere.
     *
     * <p>Un departement virtuel n'est le departement de personne : il sert a cibler
     * un evenement, et rien d'autre. Les listes qui decrivent l'ecole — Place, cibles
     * de defis, classement par departement, effectifs — passent par
     * {@code findAllByVirtualFalse()} et ne le voient pas.</p>
     */
    @Column(nullable = false)
    private boolean virtual = false;

    // --- Getters et Setters ---
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getShortName() { return shortName; }
    public void setShortName(String shortName) { this.shortName = shortName; }

    public void setKeycloakGroupName(String keycloakGroupName) { this.keycloakGroupName = keycloakGroupName; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public boolean isVirtual() { return virtual; }
    public void setVirtual(boolean virtual) { this.virtual = virtual; }

}
