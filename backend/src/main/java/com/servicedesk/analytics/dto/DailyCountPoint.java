package com.servicedesk.analytics.dto;

import java.time.LocalDate;

public record DailyCountPoint(LocalDate date, long count) {}
