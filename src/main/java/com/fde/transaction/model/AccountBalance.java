package com.fde.transaction.model;

import lombok.*;

import javax.persistence.*;

/**
 * Tracks account balances. Uses optimistic locking via @Version to prevent
 * concurrent balance updates from corrupting state.
 */
@Entity
@Table(name = "account_balances")
@Getter
@Setter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AccountBalance {

    @Id
    @EqualsAndHashCode.Include
    private String accountId;

    @Column(nullable = false, precision = 15, scale = 2)
    private java.math.BigDecimal balance;

    @Column(nullable = false, length = 3)
    private String currency;

    @Version
    private Long version;

    public AccountBalance() {
        this.balance = java.math.BigDecimal.ZERO;
        this.currency = "USD";
    }

    public AccountBalance(String accountId, String currency) {
        this.accountId = accountId;
        this.balance = java.math.BigDecimal.ZERO;
        this.currency = currency;
    }
}
