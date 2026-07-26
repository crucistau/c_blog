package xyz.crucistau.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 *
 * <p>
 * 创建时间：2023/12/4 9:42
 */
@Data
public class RoleDeleteDTO {
    @NotNull
    private List<Long> Ids;
}
