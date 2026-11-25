package com.rebuild.phoebe.web.controller;

import com.rebuild.phoebe.common.BizException;
import com.rebuild.phoebe.common.Result;
import com.rebuild.phoebe.domain.entity.EnterpriseKnowledge;
import com.rebuild.phoebe.domain.entity.ImageAsset;
import com.rebuild.phoebe.domain.service.AssetService;
import com.rebuild.phoebe.simulator.library.RecommendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.HtmlUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** 资源管理（企业知识、图片）与问答推荐。 */
@Tag(name = "资源管理与推荐")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ResourceController {
    private static final Set<String> IMAGE_EXT = Set.of("png", "jpg", "jpeg", "gif", "webp");

    private final AssetService assetService;
    private final RecommendService recommendService;

    @Value("${phoebe.upload-dir:./data/uploads}")
    private String uploadDir;

    // ---------------------------------------------------------------- 推荐

    @Operation(summary = "推荐问题：按关键词推荐高 PV 问题（一次性）")
    @GetMapping("/recommend/questions")
    public Result<List<Map<String, Object>>> byKeyword(@RequestParam(required = false) String keyword) {
        return Result.ok(recommendService.byKeyword(keyword));
    }

    @Operation(summary = "推荐问题：按单元订阅词持续推荐")
    @GetMapping("/recommend/questions/by-unit/{unitId}")
    public Result<List<Map<String, Object>>> bySubscribe(@PathVariable Long unitId) {
        return Result.ok(recommendService.bySubscribe(unitId));
    }

    @Operation(summary = "优质问答（可直接采纳或编辑后采纳）")
    @GetMapping("/recommend/quality-qas")
    public Result<List<Map<String, Object>>> quality(@RequestParam(required = false) String keyword) {
        return Result.ok(recommendService.quality(keyword));
    }

    @Operation(summary = "答案推荐")
    @GetMapping("/recommend/answers")
    public Result<List<String>> answers(@RequestParam String question) {
        return Result.ok(recommendService.answers(question));
    }

    // ---------------------------------------------------------------- 企业知识

    @GetMapping("/knowledge")
    public Result<List<EnterpriseKnowledge>> knowledge() {
        return Result.ok(assetService.knowledge());
    }

    public record KnowledgeRequest(String title, String content) {
    }

    @PostMapping("/knowledge")
    public Result<EnterpriseKnowledge> createKnowledge(@RequestBody KnowledgeRequest req) {
        return Result.ok(assetService.saveKnowledge(null, req.title(), req.content()));
    }

    @PutMapping("/knowledge/{id}")
    public Result<EnterpriseKnowledge> updateKnowledge(@PathVariable Long id, @RequestBody KnowledgeRequest req) {
        return Result.ok(assetService.saveKnowledge(id, req.title(), req.content()));
    }

    @DeleteMapping("/knowledge/{id}")
    public Result<Void> deleteKnowledge(@PathVariable Long id) {
        assetService.deleteKnowledge(id);
        return Result.ok();
    }

    // ---------------------------------------------------------------- 图片

    @GetMapping("/images")
    public Result<List<ImageAsset>> images() {
        return Result.ok(assetService.images());
    }

    @Operation(summary = "本地上传图片（可指定裁剪尺寸 1:1/4:3/16:9/3:1）")
    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ImageAsset> upload(@RequestParam("file") MultipartFile file,
                                     @RequestParam(required = false) String cropRatio) throws IOException {
        String original = file.getOriginalFilename() == null ? "image.png" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!IMAGE_EXT.contains(ext)) {
            throw BizException.badRequest("仅支持 png/jpg/jpeg/gif/webp 图片");
        }
        Path dir = Path.of(uploadDir).toAbsolutePath();
        Files.createDirectories(dir);
        String name = UUID.randomUUID() + "." + ext;
        Path target = dir.resolve(name);
        file.transferTo(target);
        Integer w = null;
        Integer h = null;
        BufferedImage img = ImageIO.read(target.toFile());
        if (img != null) {
            w = img.getWidth();
            h = img.getHeight();
        }
        return Result.ok(assetService.addImage(original, "/uploads/" + name, w, h, "UPLOAD", cropRatio));
    }

    public record RecommendImageRequest(String name, String url, Integer width, Integer height, String cropRatio) {
    }

    @Operation(summary = "系统推荐图片列表（模拟占位图）")
    @GetMapping("/images/recommendations")
    public Result<List<RecommendImageRequest>> recommendations() {
        return Result.ok(List.of(
                new RecommendImageRequest("Logo 1:1", "/api/images/placeholder?w=200&h=200&text=Logo", 200, 200, "1:1"),
                new RecommendImageRequest("产品图 4:3", "/api/images/placeholder?w=400&h=300&text=Product", 400, 300, "4:3"),
                new RecommendImageRequest("Banner 16:9", "/api/images/placeholder?w=960&h=540&text=Banner", 960, 540, "16:9"),
                new RecommendImageRequest("横幅 3:1", "/api/images/placeholder?w=960&h=320&text=Banner", 960, 320, "3:1")));
    }

    @PostMapping("/images/recommend")
    public Result<ImageAsset> addRecommended(@RequestBody RecommendImageRequest req) {
        return Result.ok(assetService.addImage(req.name(), req.url(), req.width(), req.height(), "RECOMMEND", req.cropRatio()));
    }

    @DeleteMapping("/images/{id}")
    public Result<Void> deleteImage(@PathVariable Long id) {
        assetService.deleteImage(id);
        return Result.ok();
    }

    @Operation(summary = "SVG 占位图（演示素材，无需登录）")
    @GetMapping(value = "/images/placeholder", produces = "image/svg+xml")
    public ResponseEntity<String> placeholder(@RequestParam(defaultValue = "300") int w,
                                              @RequestParam(defaultValue = "200") int h,
                                              @RequestParam(defaultValue = "Image") String text) {
        int width = Math.max(16, Math.min(w, 2000));
        int height = Math.max(16, Math.min(h, 2000));
        String t = HtmlUtils.htmlEscape(text.length() > 24 ? text.substring(0, 24) : text);
        int hue = Math.abs(text.hashCode()) % 360;
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + width + "\" height=\"" + height + "\" viewBox=\"0 0 "
                + width + " " + height + "\"><rect width=\"100%\" height=\"100%\" fill=\"hsl(" + hue + ",45%,88%)\"/>"
                + "<text x=\"50%\" y=\"50%\" dominant-baseline=\"middle\" text-anchor=\"middle\" font-family=\"sans-serif\" font-size=\""
                + Math.max(10, Math.min(width, height) / 6) + "\" fill=\"hsl(" + hue + ",35%,35%)\">" + t + "</text></svg>";
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS)).body(svg);
    }
}
