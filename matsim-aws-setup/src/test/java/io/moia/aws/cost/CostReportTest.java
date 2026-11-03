package io.moia.aws.cost;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.services.costexplorer.model.DateInterval;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageRequest;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageResponse;
import software.amazon.awssdk.services.costexplorer.model.Group;
import software.amazon.awssdk.services.costexplorer.model.GroupDefinitionType;
import software.amazon.awssdk.services.costexplorer.model.MetricValue;
import software.amazon.awssdk.services.costexplorer.model.ResultByTime;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CostReportTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 1);
    private static final LocalDate END = LocalDate.of(2026, 11, 1);

    private static Group group(String amount, String... keys) {
        return Group.builder()
                .keys(keys)
                .metrics(Map.of(CostReport.METRIC, MetricValue.builder().amount(amount).unit("USD").build()))
                .build();
    }

    private static ResultByTime month(String start, Group... groups) {
        return ResultByTime.builder()
                .timePeriod(DateInterval.builder().start(start).end(start).build())
                .groups(groups)
                .build();
    }

    @Test
    void request_groupsByAccountAndOptionallyByTag() {
        GetCostAndUsageRequest withoutTag = CostReport.request(START, END, null, null);
        assertEquals("2026-09-01", withoutTag.timePeriod().start());
        assertEquals("2026-11-01", withoutTag.timePeriod().end());
        assertEquals(List.of(CostReport.METRIC), withoutTag.metrics());
        assertEquals(1, withoutTag.groupBy().size());
        assertEquals("LINKED_ACCOUNT", withoutTag.groupBy().get(0).key());

        GetCostAndUsageRequest withTag = CostReport.request(START, END, "Owner", "token");
        assertEquals(2, withTag.groupBy().size());
        assertEquals(GroupDefinitionType.TAG, withTag.groupBy().get(1).type());
        assertEquals("Owner", withTag.groupBy().get(1).key());
        assertEquals("token", withTag.nextPageToken());
    }

    @Test
    void fetch_followsPagesAndReadsTagValues() {
        List<GetCostAndUsageRequest> requests = new ArrayList<>();
        List<CostReport.Row> rows = CostReport.fetch(request -> {
            requests.add(request);
            if (request.nextPageToken() == null) {
                return GetCostAndUsageResponse.builder()
                        .resultsByTime(month("2026-09-01", group("10.5", "111", "Owner$team-a")))
                        .nextPageToken("page2")
                        .build();
            }
            return GetCostAndUsageResponse.builder()
                    .resultsByTime(month("2026-10-01", group("4", "111", "Owner$team-a"), group("6", "111", "Owner$")))
                    .build();
        }, START, END, "Owner");

        assertEquals(2, requests.size());
        assertEquals("page2", requests.get(1).nextPageToken());
        assertEquals(List.of(
                new CostReport.Row("2026-09", "111", "team-a", new BigDecimal("10.5"), "USD"),
                new CostReport.Row("2026-10", "111", "team-a", new BigDecimal("4"), "USD"),
                new CostReport.Row("2026-10", "111", "", new BigDecimal("6"), "USD")), rows);
        assertTrue(rows.get(2).untagged());
    }

    @Test
    void taggedShare_perMonth() {
        List<CostReport.Row> rows = List.of(
                new CostReport.Row("2026-09", "111", "team-a", new BigDecimal("10"), "USD"),
                new CostReport.Row("2026-10", "111", "team-a", new BigDecimal("4"), "USD"),
                new CostReport.Row("2026-10", "111", "", new BigDecimal("6"), "USD"),
                new CostReport.Row("2026-11", "111", "", new BigDecimal("5"), "USD"));

        Map<String, BigDecimal> shares = CostReport.taggedShare(rows);

        assertEquals(0, new BigDecimal("1").compareTo(shares.get("2026-09")));
        assertEquals(0, new BigDecimal("0.4").compareTo(shares.get("2026-10")));
        assertEquals(0, BigDecimal.ZERO.compareTo(shares.get("2026-11")));
    }

    @Test
    void writeCsv_withAndWithoutTag() throws Exception {
        StringWriter withTag = new StringWriter();
        CostReport.writeCsv(List.of(new CostReport.Row("2026-10", "111", "a,b", new BigDecimal("1.50"), "USD")),
                withTag);
        assertEquals("month,account,tag_value,amount,unit\n2026-10,111,\"a,b\",1.50,USD\n", withTag.toString());

        StringWriter withoutTag = new StringWriter();
        CostReport.writeCsv(List.of(new CostReport.Row("2026-10", "111", null, new BigDecimal("2"), "USD")),
                withoutTag);
        assertEquals("month,account,amount,unit\n2026-10,111,2,USD\n", withoutTag.toString());
    }

    /**
     * Tag values may contain line breaks. Unquoted, they would split one row into several CSV records (RFC 4180).
     */
    @Test
    void writeCsv_quotesLineBreaks() throws Exception {
        StringWriter csv = new StringWriter();
        CostReport.writeCsv(List.of(
                new CostReport.Row("2026-10", "111", "a\nb", new BigDecimal("1"), "USD"),
                new CostReport.Row("2026-10", "111", "c\r\nd", new BigDecimal("2"), "USD"),
                new CostReport.Row("2026-10", "111", "e\rf", new BigDecimal("3"), "USD")), csv);
        assertEquals("month,account,tag_value,amount,unit\n"
                + "2026-10,111,\"a\nb\",1,USD\n"
                + "2026-10,111,\"c\r\nd\",2,USD\n"
                + "2026-10,111,\"e\rf\",3,USD\n", csv.toString());
    }

    /**
     * AWS tag values may start with = + - @, which spreadsheet programs evaluate as a formula (CSV injection). Such
     * values are prefixed with an apostrophe (OWASP recommendation), so they are shown as text.
     */
    @Test
    void writeCsv_neutralisesFormulas() throws Exception {
        StringWriter csv = new StringWriter();
        CostReport.writeCsv(List.of(
                new CostReport.Row("2026-10", "111", "=1+1", new BigDecimal("1"), "USD"),
                new CostReport.Row("2026-10", "111", "+a", new BigDecimal("2"), "USD"),
                new CostReport.Row("2026-10", "111", "-a", new BigDecimal("3"), "USD"),
                new CostReport.Row("2026-10", "111", "@a", new BigDecimal("4"), "USD"),
                new CostReport.Row("2026-10", "111", "a=b", new BigDecimal("5"), "USD")), csv);
        assertEquals("month,account,tag_value,amount,unit\n"
                + "2026-10,111,'=1+1,1,USD\n"
                + "2026-10,111,'+a,2,USD\n"
                + "2026-10,111,'-a,3,USD\n"
                + "2026-10,111,'@a,4,USD\n"
                + "2026-10,111,a=b,5,USD\n", csv.toString());
    }

    /**
     * Excel only detects UTF-8 in a CSV file with a byte order mark; without it, umlauts in tag values are garbled.
     */
    @Test
    void writeCsvFile_startsWithUtf8ByteOrderMark(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("costs.csv");
        CostReport.writeCsvFile(List.of(new CostReport.Row("2026-10", "111", "Müller", new BigDecimal("1"), "USD")),
                file);

        byte[] bytes = Files.readAllBytes(file);
        assertArrayEquals(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, java.util.Arrays.copyOf(bytes, 3));
        assertEquals("\uFEFFmonth,account,tag_value,amount,unit\n2026-10,111,Müller,1,USD\n",
                new String(bytes, StandardCharsets.UTF_8));
    }

    @Test
    void tagValue_ofGroupKey() {
        assertEquals("team-a", CostReport.tagValue("Owner$team-a"));
        assertEquals("", CostReport.tagValue("Owner$"));
    }
}
