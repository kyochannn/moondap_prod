package com.moondap.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.moondap.dto.MdCalendarEventDTO;

@Mapper
public interface MdCalendarEventMapper {

    List<MdCalendarEventDTO> selectAllEvents();

    MdCalendarEventDTO selectEventById(Long id);

    // 생성된 PK(id)가 전달한 DTO 에 채워진다(useGeneratedKeys).
    int insertEvent(MdCalendarEventDTO event);

    int updateEvent(MdCalendarEventDTO event);

    int deleteEvent(Long id);

    int updateCompleted(@Param("id") Long id, @Param("completed") boolean completed);

    int updateSchedule(@Param("id") Long id, @Param("startAt") LocalDate startAt, @Param("endAt") LocalDate endAt);
}
