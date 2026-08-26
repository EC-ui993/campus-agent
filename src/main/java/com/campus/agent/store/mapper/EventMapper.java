package com.campus.agent.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.agent.store.entity.Event;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EventMapper extends BaseMapper<Event> {
}
