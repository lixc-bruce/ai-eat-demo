package com.eat.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class PreferenceUpdateRequest {

    private String taste;

    private List<String> taboos;

    private String goal;

    private String scene;
}
