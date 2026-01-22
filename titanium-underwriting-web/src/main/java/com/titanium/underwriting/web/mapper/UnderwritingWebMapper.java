package com.titanium.underwriting.web.mapper;

import com.titanium.underwriting.api.dto.UnderwritingDTO;
import com.titanium.underwriting.web.vo.UnderwritingVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 核保Web层映射器 - 使用MapStruct进行DTO到VO的转换
 * 符合项目规约第6条：涉及到实体跨层转换时，必须使用Mapper类（MapStruct）进行转换
 */
@Mapper(componentModel = "spring")
public interface UnderwritingWebMapper {

    UnderwritingWebMapper INSTANCE = Mappers.getMapper(UnderwritingWebMapper.class);

    // ========== DTO到VO的映射 ==========

    /**
     * DTO转换为VO
     */
    UnderwritingVO toVO(UnderwritingDTO dto);

    /**
     * DTO列表转换为VO列表
     */
    List<UnderwritingVO> toVOList(List<UnderwritingDTO> dtoList);
}