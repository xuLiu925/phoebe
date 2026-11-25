package com.rebuild.phoebe.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rebuild.phoebe.common.BizException;
import com.rebuild.phoebe.common.UserContext;
import com.rebuild.phoebe.domain.entity.EnterpriseKnowledge;
import com.rebuild.phoebe.domain.entity.ImageAsset;
import com.rebuild.phoebe.domain.mapper.EnterpriseKnowledgeMapper;
import com.rebuild.phoebe.domain.mapper.ImageAssetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/** 资源管理 [P:A]：企业知识管理、图片管理。 */
@Service
@RequiredArgsConstructor
public class AssetService {
    /** 4 种裁剪尺寸 [P:A 存在]，具体比例 [U]。 */
    public static final Set<String> CROP_RATIOS = Set.of("1:1", "4:3", "16:9", "3:1");

    private final EnterpriseKnowledgeMapper knowledgeMapper;
    private final ImageAssetMapper imageMapper;
    private final Guard guard;
    private final OpLogService opLog;

    public List<EnterpriseKnowledge> knowledge() {
        return knowledgeMapper.selectList(new LambdaQueryWrapper<EnterpriseKnowledge>()
                .eq(EnterpriseKnowledge::getAccountId, UserContext.requireAccountId()).orderByDesc(EnterpriseKnowledge::getId));
    }

    public EnterpriseKnowledge saveKnowledge(Long id, String title, String content) {
        if (title == null || title.isBlank() || content == null || content.isBlank()) {
            throw BizException.badRequest("标题和内容不能为空");
        }
        EnterpriseKnowledge k;
        if (id == null) {
            k = new EnterpriseKnowledge();
            k.setAccountId(UserContext.requireAccountId());
        } else {
            k = guard.knowledge(id);
        }
        k.setTitle(title.trim());
        k.setContent(content.trim());
        if (id == null) {
            knowledgeMapper.insert(k);
        } else {
            knowledgeMapper.updateById(k);
        }
        opLog.log(k.getAccountId(), "KNOWLEDGE", k.getId(), id == null ? "CREATE" : "UPDATE", title);
        return k;
    }

    public void deleteKnowledge(Long id) {
        EnterpriseKnowledge k = guard.knowledge(id);
        knowledgeMapper.deleteById(id);
        opLog.log(k.getAccountId(), "KNOWLEDGE", id, "DELETE", k.getTitle());
    }

    public List<ImageAsset> images() {
        return imageMapper.selectList(new LambdaQueryWrapper<ImageAsset>()
                .eq(ImageAsset::getAccountId, UserContext.requireAccountId()).orderByDesc(ImageAsset::getId));
    }

    public ImageAsset addImage(String name, String url, Integer width, Integer height, String source, String cropRatio) {
        if (cropRatio != null && !cropRatio.isBlank() && !CROP_RATIOS.contains(cropRatio)) {
            throw BizException.badRequest("裁剪尺寸仅支持 " + CROP_RATIOS);
        }
        ImageAsset img = new ImageAsset();
        img.setAccountId(UserContext.requireAccountId());
        img.setName(name);
        img.setUrl(url);
        img.setWidth(width);
        img.setHeight(height);
        img.setSource(source == null ? "UPLOAD" : source);
        img.setCropRatio(cropRatio);
        imageMapper.insert(img);
        opLog.log(img.getAccountId(), "IMAGE", img.getId(), "CREATE", name);
        return img;
    }

    public void deleteImage(Long id) {
        ImageAsset img = guard.image(id);
        imageMapper.deleteById(id);
        opLog.log(img.getAccountId(), "IMAGE", id, "DELETE", img.getName());
    }
}
