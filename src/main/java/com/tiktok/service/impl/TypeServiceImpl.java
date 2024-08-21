package com.tiktok.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tiktok.entity.video.Type;
import com.tiktok.mapper.TypeMapper;
import com.tiktok.service.TypeService;
import org.springframework.stereotype.Service;

import java.sql.Array;
import java.util.*;

@Service
public class TypeServiceImpl extends ServiceImpl<TypeMapper, Type> implements TypeService {

    @Override
    public List<String> getLabels(Long typeId) {
        List<String> strings = this.getById(typeId).buildLabel();
        return strings;
    }

    @Override
    public List<String> random10Labels() {
        List<Type> types = new ArrayList<>();
        Collections.shuffle(types);
        ArrayList<String> labels = new ArrayList<>();
        for (Type type : types) {
            for (String label : type.buildLabel()) {
                if (labels.size() == 10) {
                    return labels;
                }
                labels.add(label);
            }
        }
        return labels;
    }
}
