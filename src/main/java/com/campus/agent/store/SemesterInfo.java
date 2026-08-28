package com.campus.agent.store;

import java.time.LocalDate;

/** 学期配置读模型。 */
public record SemesterInfo(LocalDate start, int totalWeeks) {
}
