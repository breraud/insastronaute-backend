package fr.insastronaute.webapi.treasurehunt.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.CascadeType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;

import java.util.ArrayList;
import java.util.List;

@Entity @Table(name = "treasure_hunt_vault")
public class TreasureHuntVault {
    @Id private Boolean id = true;
    @OneToMany(mappedBy = "vault", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position asc")
    private List<TreasureHuntContent> contents = new ArrayList<>();
    public Boolean getId() { return id; }
    public List<TreasureHuntContent> getContents() { return contents; }
}
