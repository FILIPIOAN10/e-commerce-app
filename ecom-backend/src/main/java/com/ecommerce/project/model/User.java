package com.ecommerce.project.model;


import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Data
@NoArgsConstructor
@Table( name = "users",
                uniqueConstraints = {
                @UniqueConstraint(columnNames = "username"),
                @UniqueConstraint(columnNames = "email")
                })
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;


    @NotEmpty
    @Size(max = 20)
    @Column(name = "username")
    private String userName;
    @NotEmpty
    @Size(max = 50)
    @Email
    @Column(name = "email")
    private String email;
    @NotEmpty
    @Size(max = 120)
    @Column(name = "password")
    private String password;

    @Size(max = 100)
    @Column(name = "password_hint")
    private String passwordHint;

    @Column(name = "provider")
    private String provider; // LOCAL, GITHUB, GOOGLE

    @Column(name = "provider_id")
    private String providerId;

    public User(String userName, String email, String password) {
        this.userName = userName;
        this.email = email;
        this.password = password;
    }




    // relationships between tables
    @Setter
    @Getter
    @ManyToMany(cascade = {CascadeType.MERGE},
                fetch = FetchType.EAGER)
    @JoinTable( name = "user_role",
                joinColumns = @JoinColumn(name = "user_id"),
                inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    @Getter
    @Setter
    @OneToMany(mappedBy = "user", cascade = {CascadeType.PERSIST, CascadeType.MERGE},orphanRemoval = true)
//    @JoinTable(name = "user_address",
//                joinColumns = @JoinColumn(name = "user_id"),
//                inverseJoinColumns = @JoinColumn(name = "address_id"))
    private List<Address> addresses = new ArrayList<>();

    @ToString.Exclude
    @OneToOne(mappedBy = "user",cascade = {CascadeType.PERSIST, CascadeType.MERGE},orphanRemoval = true)
    private Cart cart;


    @ToString.Exclude
    @OneToMany(mappedBy = "user",cascade = {CascadeType.PERSIST, CascadeType.MERGE},
                orphanRemoval = true)
    private Set<Product> products;
    @Column(name = "two_factor_secret")
    private String twoFactorSecret;

    @Column(name = "two_factor_enabled", nullable = false, columnDefinition = "boolean default false")
    private boolean twoFactorEnabled = false;

    @Column(name = "verified", nullable = false, columnDefinition = "boolean default false")
    private boolean verified = false;
}
