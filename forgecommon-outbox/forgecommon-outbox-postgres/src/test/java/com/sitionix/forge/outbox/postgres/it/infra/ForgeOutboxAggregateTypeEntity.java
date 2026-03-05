package com.sitionix.forge.outbox.postgres.it.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "forge_outbox_aggregate_types")
public class ForgeOutboxAggregateTypeEntity {

    @Id
    private Long id;

    @Column(name = "description")
    private String description;

    public Long getId() {
        return this.id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }
}
