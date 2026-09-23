package br.com.nexapay.payment.service;

import br.com.nexapay.payment.domain.RecurrenceFrequency;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class RecurrenceCalculator {

    public OffsetDateTime next(
            OffsetDateTime current,
            RecurrenceFrequency frequency,
            int anchorDay) {

        return switch (frequency) {
            case DAILY -> current.plusDays(1);
            case WEEKLY -> current.plusWeeks(1);
            case MONTHLY -> nextMonthly(current, anchorDay);
        };
    }

    private OffsetDateTime nextMonthly(OffsetDateTime current, int anchorDay) {
        OffsetDateTime firstDayNextMonth = current.plusMonths(1).withDayOfMonth(1);
        int day = Math.min(anchorDay, firstDayNextMonth.toLocalDate().lengthOfMonth());
        return firstDayNextMonth.withDayOfMonth(day);
    }
}
