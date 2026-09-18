package com.ewb.order.util;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.YearMonth;

public class ScheduleCalculator {

    /**
     * Calculates the next scheduled occurrence based on schedule rules:
     * - Specified day of month, execution time, and time zone
     * - If day of month does not exist in the target month (e.g. day 31 in Feb/Apr),
     *   the last calendar day of that month is used.
     * - Must be on or after the start date and reference time.
     */
    public static ZonedDateTime calculateNextExecution(
            int dayOfMonth,
            LocalTime executionTime,
            String timeZoneStr,
            LocalDate startDate,
            LocalDate endDate,
            ZonedDateTime referenceTime) {

        ZoneId zoneId = ZoneId.of(timeZoneStr);
        ZonedDateTime candidateZdt = referenceTime.withZoneSameInstant(zoneId);

        // Determine starting year/month
        LocalDate refDate = candidateZdt.toLocalDate();
        LocalDate searchDate = refDate.isBefore(startDate) ? startDate : refDate;

        YearMonth ym = YearMonth.from(searchDate);

        for (int i = 0; i < 120; i++) { // Search up to 10 years into the future
            int maxDayInMonth = ym.lengthOfMonth();
            int actualDay = Math.min(dayOfMonth, maxDayInMonth);
            LocalDate candidateDate = ym.atDay(actualDay);

            if (!candidateDate.isBefore(startDate)) {
                ZonedDateTime scheduledZdt = candidateDate.atTime(executionTime).atZone(zoneId);
                // If candidate is strictly after reference time (or equals)
                if (!scheduledZdt.isBefore(candidateZdt)) {
                    if (endDate != null && candidateDate.isAfter(endDate)) {
                        return null; // Exceeded end date
                    }
                    return scheduledZdt;
                }
            }
            ym = ym.plusMonths(1);
        }

        return null;
    }

    /**
     * Calculates the subsequent occurrence after a given execution time.
     */
    public static ZonedDateTime calculateSubsequentExecution(
            int dayOfMonth,
            LocalTime executionTime,
            String timeZoneStr,
            LocalDate endDate,
            ZonedDateTime currentExecutionTime) {

        ZoneId zoneId = ZoneId.of(timeZoneStr);
        ZonedDateTime zdt = currentExecutionTime.withZoneSameInstant(zoneId);
        YearMonth nextYm = YearMonth.from(zdt.toLocalDate()).plusMonths(1);

        int maxDayInMonth = nextYm.lengthOfMonth();
        int actualDay = Math.min(dayOfMonth, maxDayInMonth);
        LocalDate nextDate = nextYm.atDay(actualDay);

        if (endDate != null && nextDate.isAfter(endDate)) {
            return null; // Expired
        }

        return nextDate.atTime(executionTime).atZone(zoneId);
    }
}
