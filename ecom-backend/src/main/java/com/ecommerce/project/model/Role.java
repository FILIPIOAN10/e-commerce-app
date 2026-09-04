package com.ecommerce.project.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;


@Entity
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Table( name="roles")
public class Role
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="role_id")
    @EqualsAndHashCode.Include
    @ToString.Include
    private Integer roleId;


    @ToString.Exclude
    @Enumerated(EnumType.STRING)
    @Column(length=20 , name = "role_name")
    private AppRole roleName;



    public Role(AppRole roleName) {
        this.roleName = roleName;
    }
}
