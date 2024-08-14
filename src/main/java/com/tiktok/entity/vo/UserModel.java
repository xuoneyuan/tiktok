package com.tiktok.entity.vo;


import com.tiktok.entity.user.UserHolder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UserModel {
    private List<Model> models;
    private Long userId;

    public static UserModel buildUserModel(List<String> labels,Long videoId,Double score){
        UserModel userModel = new UserModel();
        ArrayList<Model> models = new ArrayList<>();
        userModel.setUserId(UserHolder.get());
        for (String label : labels) {
            Model model = new Model();
            model.setLabel(label);
            model.setScore(score);
            model.setVideoId(videoId);
            models.add(model);
        }
        userModel.setModels(models);
        return userModel;
    }
}
