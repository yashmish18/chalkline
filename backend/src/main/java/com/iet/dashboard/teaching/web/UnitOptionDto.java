package com.iet.dashboard.teaching.web;

import com.iet.dashboard.academics.web.SyllabusTopicDto;
import java.util.List;

public record UnitOptionDto(
        int unitNumber,
        String unitTitle,
        List<SyllabusTopicDto> topics
) {
}
