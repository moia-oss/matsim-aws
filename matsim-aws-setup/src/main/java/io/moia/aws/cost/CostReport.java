package io.moia.aws.cost;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.DateInterval;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageRequest;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageResponse;
import software.amazon.awssdk.services.costexplorer.model.Granularity;
import software.amazon.awssdk.services.costexplorer.model.Group;
import software.amazon.awssdk.services.costexplorer.model.GroupDefinition;
import software.amazon.awssdk.services.costexplorer.model.GroupDefinitionType;
import software.amazon.awssdk.services.costexplorer.model.MetricValue;
import software.amazon.awssdk.services.costexplorer.model.ResultByTime;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Monthly AWS costs per account and, optionally, per value of a tag, read from AWS Cost Explorer.
 * <p>
 * Usage: {@code CostReport <start yyyy-MM-dd> <end yyyy-MM-dd, exclusive> [tag key] [output.csv]}. Without an
 * output file the CSV is printed. With a tag key, the report also shows how much of the cost carries a value of that
 * tag: if nothing does, either the tag key is not activated as a cost allocation tag in the management account, or
 * the resources that cause the costs are not tagged.
 * <p>
 * Cost Explorer does not show split cost allocation data (costs of shared EC2 instances attributed to ECS tasks / Batch
 * jobs); those are only reported in the Cost and Usage Report (Data Exports).
 */
public final class CostReport {

    public static final String METRIC = "UnblendedCost";

    /**
     * One line of the report.
     *
     * @param tagValue the tag value, or {@code null} if the report is not grouped by a tag; empty if the cost carries
     *                 no value of the tag
     */
    public record Row(String month, String account, String tagValue, BigDecimal amount, String unit) {
        public boolean untagged() {
            return tagValue != null && tagValue.isEmpty();
        }
    }

    private CostReport() {
    }

    /**
     * Builds the request for monthly costs grouped by account and, if given, by a tag key.
     */
    public static GetCostAndUsageRequest request(LocalDate start, LocalDate end, String tagKey, String nextPageToken) {
        List<GroupDefinition> groupBy = new ArrayList<>();
        groupBy.add(GroupDefinition.builder().type(GroupDefinitionType.DIMENSION).key("LINKED_ACCOUNT").build());
        if (tagKey != null) {
            groupBy.add(GroupDefinition.builder().type(GroupDefinitionType.TAG).key(tagKey).build());
        }
        return GetCostAndUsageRequest.builder()
                .timePeriod(DateInterval.builder().start(start.toString()).end(end.toString()).build())
                .granularity(Granularity.MONTHLY)
                .metrics(METRIC)
                .groupBy(groupBy)
                .nextPageToken(nextPageToken)
                .build();
    }

    /**
     * Fetches all pages of the report.
     *
     * @param costSource usually {@code CostExplorerClient::getCostAndUsage}
     */
    public static List<Row> fetch(Function<GetCostAndUsageRequest, GetCostAndUsageResponse> costSource,
                                  LocalDate start, LocalDate end, String tagKey) {
        List<Row> rows = new ArrayList<>();
        String nextPageToken = null;
        do {
            GetCostAndUsageResponse response = costSource.apply(request(start, end, tagKey, nextPageToken));
            for (ResultByTime result : response.resultsByTime()) {
                rows.addAll(rows(result, tagKey != null));
            }
            nextPageToken = response.nextPageToken();
        } while (nextPageToken != null && !nextPageToken.isEmpty());
        return rows;
    }

    static List<Row> rows(ResultByTime result, boolean groupedByTag) {
        String month = result.timePeriod().start().substring(0, 7);
        List<Row> rows = new ArrayList<>();
        for (Group group : result.groups()) {
            MetricValue cost = group.metrics().get(METRIC);
            String account = group.keys().get(0);
            String tagValue = groupedByTag ? tagValue(group.keys().get(1)) : null;
            rows.add(new Row(month, account, tagValue, new BigDecimal(cost.amount()), cost.unit()));
        }
        return rows;
    }

    /**
     * Cost Explorer returns tag groups as {@code key$value}, and {@code key$} for costs without a value of the tag.
     */
    static String tagValue(String groupKey) {
        int separator = groupKey.indexOf('$');
        return separator < 0 ? groupKey : groupKey.substring(separator + 1);
    }

    /**
     * @return per month the share of the cost that carries a value of the tag (0 to 1)
     */
    public static Map<String, BigDecimal> taggedShare(List<Row> rows) {
        Map<String, BigDecimal[]> sums = new LinkedHashMap<>();
        for (Row row : rows) {
            BigDecimal[] sum = sums.computeIfAbsent(row.month(), m -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            sum[1] = sum[1].add(row.amount());
            if (!row.untagged()) {
                sum[0] = sum[0].add(row.amount());
            }
        }
        Map<String, BigDecimal> shares = new LinkedHashMap<>();
        sums.forEach((month, sum) -> shares.put(month, sum[1].signum() == 0 ?
                BigDecimal.ZERO :
                sum[0].divide(sum[1], 4, RoundingMode.HALF_UP)));
        return shares;
    }

    /**
     * The CSV format of the report: RFC 4180 quoting and escaping, records separated by {@code \n}.
     */
    static final CSVFormat CSV_FORMAT = CSVFormat.RFC4180.builder().setRecordSeparator('\n').get();

    public static void writeCsv(List<Row> rows, Writer writer) throws IOException {
        boolean withTag = rows.stream().anyMatch(row -> row.tagValue() != null);
        // not closed: the writer belongs to the caller
        CSVPrinter printer = new CSVPrinter(writer, CSV_FORMAT);
        if (withTag) {
            printer.printRecord("month", "account", "tag_value", "amount", "unit");
        } else {
            printer.printRecord("month", "account", "amount", "unit");
        }
        for (Row row : rows) {
            if (withTag) {
                printer.printRecord(row.month(), row.account(),
                        neutraliseFormula(row.tagValue() == null ? "" : row.tagValue()),
                        row.amount().toPlainString(), row.unit());
            } else {
                printer.printRecord(row.month(), row.account(), row.amount().toPlainString(), row.unit());
            }
        }
        printer.flush();
    }

    /**
     * Prefixes values starting with = + - @ with an apostrophe, so that spreadsheet programs show them as text instead
     * of evaluating them as a formula (CSV injection, see OWASP). AWS allows these characters in tag values.
     */
    static String neutraliseFormula(String value) {
        return !value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
    }

    /**
     * Writes the report to a file in UTF-8 with a byte order mark, so that Excel detects the encoding (otherwise
     * umlauts in tag values are garbled). The standard output is written without it.
     */
    public static void writeCsvFile(List<Row> rows, Path file) throws IOException {
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write('\uFEFF');
            writeCsv(rows, writer);
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: CostReport <start yyyy-MM-dd> <end yyyy-MM-dd, exclusive> [tag key] [output.csv]");
            System.exit(1);
        }
        LocalDate start = LocalDate.parse(args[0]);
        LocalDate end = LocalDate.parse(args[1]);
        String tagKey = args.length > 2 && !args[2].isBlank() ? args[2] : null;

        List<Row> rows;
        // Cost Explorer is a global service served from us-east-1
        try (CostExplorerClient client = CostExplorerClient.builder().region(Region.US_EAST_1).build()) {
            rows = fetch(client::getCostAndUsage, start, end, tagKey);
        }

        if (args.length > 3) {
            writeCsvFile(rows, Path.of(args[3]));
            System.out.println("Written " + rows.size() + " rows to " + args[3]);
        } else {
            Writer writer = new OutputStreamWriter(System.out, StandardCharsets.UTF_8);
            writeCsv(rows, writer);
            writer.flush(); // do not close System.out
        }

        if (tagKey != null) {
            System.err.println("Share of cost with a value of tag '" + tagKey + "':");
            taggedShare(rows).forEach((month, share) -> System.err.println("  " + month + ": "
                    + share.movePointRight(2).stripTrailingZeros().toPlainString() + " %"));
            System.err.println("0 % means: the tag key is not activated as a cost allocation tag in the management "
                    + "account, or the resources causing the costs are not tagged.");
        }
    }
}
