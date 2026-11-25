package com.rebuild.phoebe.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ExcelImportResult {
    private int totalRows;
    private int success;
    /** 超过单次上限被忽略的行数 [P:A]。 */
    private int ignored;
    private List<RowError> errors = new ArrayList<>();

    @Data
    @AllArgsConstructor
    public static class RowError {
        private int row;
        private String question;
        private String reason;
    }
}
