package com.eat.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class FavoriteListResponse {

    private List<DateGroup> dateGroups;

    private long total;

    private int page;

    private int size;

    @Data
    public static class DateGroup {
        private String date;
        private List<FavoriteItem> items;
    }

    @Data
    public static class FavoriteItem {
        private Long id;
        private String mealPeriod;
        private String planType;
        private String planTitle;
        private List<String> planItems;
        private String estTime;
        private String calorieRange;
        private String createdAt;
    }
}
