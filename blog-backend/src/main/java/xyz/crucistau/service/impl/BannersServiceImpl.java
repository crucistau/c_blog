package xyz.crucistau.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import xyz.crucistau.constants.RespConst;
import xyz.crucistau.constants.SQLConst;
import xyz.crucistau.domain.entity.Banners;
import xyz.crucistau.domain.response.ResponseResult;
import xyz.crucistau.enums.UploadEnum;
import xyz.crucistau.exceptions.FileUploadException;
import xyz.crucistau.mapper.BannersMapper;
import xyz.crucistau.service.BannersService;
import xyz.crucistau.utils.FileUploadUtils;
import xyz.crucistau.utils.SecurityUtils;

import java.util.List;

/**
 * (Banners)表服务实现类
 *
 *
 * @since 2024-08-28 09:51:22
 */
@Service("bannersService")
public class BannersServiceImpl extends ServiceImpl<BannersMapper, Banners> implements BannersService {

    @Resource
    private BannersMapper bannersMapper;

    @Resource
    private FileUploadUtils fileUploadUtils;

    @Value("${minio.endpoint}")
    private String minioEndpoint;

    @Value("${minio.bucketName}")
    private String bucketName;

    @Override
    public List<String> getBanners() {
        List<Banners> banners = bannersMapper.selectList(new LambdaQueryWrapper<Banners>().orderByAsc(Banners::getSortOrder));
        if (!banners.isEmpty()) {
            return banners.stream().map(b -> {
                String path = b.getPath();
                int idx = path.indexOf("/" + bucketName + "/");
                if (idx != -1) {
                    // 用当前配置的 endpoint 替换旧域名
                    return minioEndpoint + path.substring(idx);
                }
                return path;
            }).toList();
        }
        return List.of();
    }

    @Override
    public List<Banners> backGetBanners() {
        List<Banners> banners = bannersMapper.selectList(new LambdaQueryWrapper<Banners>().orderByAsc(Banners::getSortOrder));
        if (!banners.isEmpty()){
            return banners;
        }
        return List.of();
    }

    @Transactional
    @Override
    public ResponseResult<Banners> uploadBannerImage(MultipartFile bannerImage) {
        try {
            String bannerUrl;
            try {
                // 是否到达Banner数量上限
                if (bannersMapper.selectCount(null) >= SQLConst.BANNER_MAX_COUNT) {
                    return ResponseResult.failure(RespConst.BANNER_MAX_COUNT_MSG);
                }
                bannerUrl = fileUploadUtils.upload(UploadEnum.UI_BANNERS, bannerImage);
                Banners banner = Banners.builder().size(bannerImage.getSize())
                        .type(bannerImage.getContentType())
                        .userId(SecurityUtils.getUserId())
                        .sortOrder((int) (bannersMapper.selectCount(null) + 1))
                        .path(bannerUrl).build();
                bannersMapper.insert(banner);
                return ResponseResult.success(banner);
            } catch (FileUploadException e) {
                return ResponseResult.failure(e.getMessage());
            }
        } catch (Exception e) {
            log.error(UploadEnum.UI_BANNERS.getDescription() + "上传失败", e);
            return ResponseResult.failure();
        }
    }

    @Override
    public ResponseResult<String> removeBannerById(Long id) {
        Banners banner = bannersMapper.selectById(id);
        if (this.removeById(id)) {
            // minio是否存在
            if (fileUploadUtils.isFileExist(UploadEnum.UI_BANNERS.getDir(), fileUploadUtils.getFileName(banner.getPath()))) {
                fileUploadUtils.deleteFile(UploadEnum.UI_BANNERS.getDir(), fileUploadUtils.getFileName(banner.getPath()));
            }
        } else return ResponseResult.failure("删除失败");
        return ResponseResult.success("删除成功");
    }

    @Override
    public ResponseResult<String> updateSortOrder(List<Banners> banners) {
        // 删除全部
        bannersMapper.delete(Wrappers.emptyWrapper());
        //  重新排序
        for (int i = 0; i < banners.size(); i++) {
            banners.get(i).setSortOrder(i + 1);
            bannersMapper.insert(banners.get(i));
        }
        return ResponseResult.success();
    }

}
