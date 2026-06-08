package com.eat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class QaReplyResponse {

    private String answer;

    private String sessionId;
}
