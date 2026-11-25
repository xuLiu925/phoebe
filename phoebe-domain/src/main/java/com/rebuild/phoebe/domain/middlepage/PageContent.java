package com.rebuild.phoebe.domain.middlepage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 中间页内容（PC 或无线一份）。
 * <p>
 * 三大模块 [P:A]：企业信息（右侧）、图片 banner（上方）、产品介绍区（下方）。
 * 子字段（Logo、咨询按钮、二维码、官网、APP 下载等）来自资料 C/E [P]；字段名与 JSON 存储形式 [U]。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PageContent {
    private CompanyInfo companyInfo = new CompanyInfo();
    private List<Banner> banners = new ArrayList<>();
    private List<Product> products = new ArrayList<>();
    /** 无线端 APP 下载链接 [P:C]。 */
    private String appDownloadUrl;
    /** 视频物料 [P:A 提及]，仅预留。 */
    private String videoUrl;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CompanyInfo {
        private String name;
        private String logo;
        private String intro;
        private String website;
        /** 咨询按钮链接（原产品默认商桥链接）[P:E]。 */
        private String consultUrl;
        /** 咨询电话：不自动填充，需要手动填写 [P:E]。 */
        private String phone;
        /** 二维码图片（PC 右侧 banner 区）[P:C]。 */
        private String qrCode;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Banner {
        private String image;
        private String url;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Product {
        private String image;
        private String title;
        private String desc;
        private String url;
    }
}
