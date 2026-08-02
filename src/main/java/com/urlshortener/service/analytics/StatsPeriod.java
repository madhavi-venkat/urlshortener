package com.urlshortener.service.analytics;

/**
 * Time-bucket granularity for the click drill-down. Bound straight from the
 * {@code ?period=} query param (Spring converts the enum name, case-insensitively), so
 * the value reaching the database is always one of these three literals — never
 * arbitrary user input, even though the underlying query is parameterized either way.
 */
public enum StatsPeriod {
    DAY("day"),
    WEEK("week"),
    MONTH("month");

    private final String sqlUnit;

    StatsPeriod(String sqlUnit) {
        this.sqlUnit = sqlUnit;
    }

    /** The unit literal passed to Postgres {@code date_trunc}. */
    public String sqlUnit() {
        return sqlUnit;
    }
}
