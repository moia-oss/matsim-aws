package io.moia.aws.infra;

import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;

import java.util.Arrays;

public class PolicyStatementParser {

    private PolicyStatementParser() {}

    // Parses a comma-separated list of role ARNs into PolicyStatements.
    public static PolicyStatement[] parse(String csv) {
        if (csv == null || csv.isBlank()) return new PolicyStatement[0];

        return Arrays.stream(csv.split(","))
                .map(String::strip)
                .filter(arn -> !arn.isBlank())
                .map(PolicyStatementParser::assumeRoleStatement)
                .toArray(PolicyStatement[]::new);
    }

    private static PolicyStatement assumeRoleStatement(String roleArn) {
        PolicyStatement statement = new PolicyStatement();
        statement.setEffect(Effect.ALLOW);
        statement.addActions("sts:AssumeRole");
        statement.addResources(roleArn);
        return statement;
    }
}
