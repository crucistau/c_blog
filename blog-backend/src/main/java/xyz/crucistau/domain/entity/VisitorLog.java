package xyz.crucistau.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import xyz.crucistau.domain.BaseData;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("t_visitor_log")
public class VisitorLog implements Serializable, BaseData {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String ip;
    private String address;
    private String browser;
    private String os;
    private String pageUrl;
    private String userAgent;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    private Integer isDeleted;
}
