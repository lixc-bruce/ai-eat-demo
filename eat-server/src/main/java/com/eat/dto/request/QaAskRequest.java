package com.eat.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class QaAskRequest {

    private String sessionId;

    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题长度不能超过500字")
    private String question;
}
