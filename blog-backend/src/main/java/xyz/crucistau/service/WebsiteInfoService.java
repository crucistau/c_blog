package xyz.crucistau.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;
import xyz.crucistau.domain.dto.StationmasterInfoDTO;
import xyz.crucistau.domain.dto.WebsiteInfoDTO;
import xyz.crucistau.domain.entity.WebsiteInfo;
import xyz.crucistau.domain.response.ResponseResult;
import xyz.crucistau.domain.vo.WebsiteInfoVO;
import xyz.crucistau.enums.UploadEnum;


/**
 * (WebsiteInfo)表服务接口
 *
 * @author kuailemao
 * @since 2023-12-27 14:07:34
 */
public interface WebsiteInfoService extends IService<WebsiteInfo> {

    /**
     * 上传或更新图片
     *
     * @param uploadEnum 目录
     * @param avatar     图片
     * @param type       类型
     * @return 是否成功&图片地址
     */
    ResponseResult<String> uploadImageInsertOrUpdate(UploadEnum uploadEnum, MultipartFile avatar, Integer type);

    /**
     * 查询网站信息
     * @return 网站信息
     */
    WebsiteInfoVO selectWebsiteInfo();

    /**
     * 修改站长信息
     * @param stationmasterInfoDTO 站长信息
     * @return 是否成功
     */
    ResponseResult<Void> updateStationmasterInfo(StationmasterInfoDTO stationmasterInfoDTO);

    /**
     * 修改网站信息
     * @param websiteInfoDTO 网站信息
     * @return 是否成功
     */
    ResponseResult<Void> updateWebsiteInfo(WebsiteInfoDTO websiteInfoDTO);
}
