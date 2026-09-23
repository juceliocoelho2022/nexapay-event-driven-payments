package br.com.nexapay.payment.service;

import br.com.nexapay.payment.domain.RecurrenceFrequency;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RecurrenceCalculatorTest {

    private final RecurrenceCalculator calculator = new RecurrenceCalculator();

    @Test
    void shouldAdvanceDailyAndWeekly() {
        OffsetDateTime current = OffsetDateTime.of(
                2026, 10, 5, 9, 0, 0, 0, ZoneOffset.of("-03:00")
        );

        assertThat(calculator.next(current, RecurrenceFrequency.DAILY, 5))
                .isEqualTo(current.plusDays(1));

        assertThat(calculator.next(current, RecurrenceFrequency.WEEKLY, 5))
                .isEqualTo(current.plusWeeks(1));
    }

    @Test
    void shouldPreserveMonthlyAnchorDayAcrossShortMonths() {
        OffsetDateTime january31 = OffsetDateTime.of(
                2027, 1, 31, 9, 0, 0, 0, ZoneOffset.of("-03:00")
        );

        OffsetDateTime february = calculator.next(
                january31,
                RecurrenceFrequency.MONTHLY,
                31
        );

        OffsetDateTime march = calculator.next(
                february,
                RecurrenceFrequency.MONTHLY,
                31
        );

        assertThat(february.getDayOfMonth()).isEqualTo(28);
        assertThat(february.getMonthValue()).isEqualTo(2);
        assertThat(march.getDayOfMonth()).isEqualTo(31);
        assertThat(march.getMonthValue()).isEqualTo(3);
    }
}
