package com.strapdata.elassandraunit.example;

import com.datastax.driver.mapping.annotations.PartitionKey;
import com.datastax.driver.mapping.annotations.Table;
import lombok.*;
import lombok.experimental.Wither;

@Table(name = "users",
    readConsistency = "LOCAL_QUORUM",
    writeConsistency = "LOCAL_QUORUM",
    caseSensitiveKeyspace = false,
    caseSensitiveTable = false)
@ToString(includeFieldNames=true)
@Getter
@Setter
@EqualsAndHashCode
@Wither
@NoArgsConstructor
@AllArgsConstructor
public class User {

    String firstname;

    String lastname;

    @PartitionKey(0)
    String email;
}
