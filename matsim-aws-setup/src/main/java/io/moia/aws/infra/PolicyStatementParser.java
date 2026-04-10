package io.moia.aws.infra;

import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;

import java.util.ArrayList;
import java.util.List;

public class PolicyStatementParser {

    private PolicyStatementParser() {}

    // Parses a CSV string into PolicyStatements. Each line: Effect,Action,Resource
    // e.g. "Allow,s3:GetObject,arn:aws:s3:::my-bucket/*"
    public static PolicyStatement[] parse(String csv) {
        if (csv == null || csv.isBlank()) return new PolicyStatement[0];

        String[] lines = csv.split("\n");
        List<PolicyStatement> statements = new ArrayList<>();
        int rowNumber = 0;

        for (String raw : lines) {
            String line = raw.strip();
            if (line.isBlank()) continue;
            rowNumber++;

            String[] parts = line.split(",", 3);
            if (parts.length != 3) {
                throw new IllegalArgumentException(
                        "IAM_POLICY_CSV row " + rowNumber + " has " + parts.length + " column(s) but expected 3.\n" +
                        "  Row:     \"" + line + "\"\n" +
                        "  Format:  Effect,Action,Resource\n" +
                        "  Example: Allow,s3:GetObject,arn:aws:s3:::my-bucket/*");
            }

            String effectStr = parts[0].strip();
            Effect effect;
            try {
                effect = Effect.valueOf(effectStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "IAM_POLICY_CSV row " + rowNumber + " has an invalid Effect value: \"" + effectStr + "\".\n" +
                        "  Row:    \"" + line + "\"\n" +
                        "  Valid values: Allow, Deny");
            }

            statements.add(PolicyStatement.Builder.create()
                    .effect(effect)
                    .actions(List.of(parts[1].strip()))
                    .resources(List.of(parts[2].strip()))
                    .build());
        }

        return statements.toArray(new PolicyStatement[0]);
    }
}
