package com.tiktok.entity.json;

import lombok.Data;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

@Data
public class BodyJson implements Serializable {
    String id;
    String status;
    ResultJson result;


    public boolean compare (Double min, Double max, Double value){
        return value >= min && value <= max;
    }


    public boolean checkViolation(List<CutsJson> types, Double min, Double max){
        for (CutsJson cutsJson : types) {
            if (!cutsJson.getDetails().isEmpty()){
                for (DetailsJson detail : cutsJson.details) {
                    if (compare(min,max,detail.getScore())){
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public List<CutsJson> getTerror(){
        TypeJson terror = result.getResult().getScenes().getTerror();
        if(!terror.getCuts().isEmpty()){
            return terror.getCuts();
        }

        CutsJson cutsJson = new CutsJson();
        cutsJson.setDetails(terror.getDetails());
        cutsJson.setSuggestion(terror.getSuggestion());
        return Collections.singletonList(cutsJson);

    }


    public List<CutsJson> getPolitician(){
        TypeJson terror = result.getResult().getScenes().getPolitician();
        if(!terror.getCuts().isEmpty()){
            return terror.getCuts();
        }

        CutsJson cutsJson = new CutsJson();
        cutsJson.setDetails(terror.getDetails());
        cutsJson.setSuggestion(terror.getSuggestion());
        return Collections.singletonList(cutsJson);

    }

    public List<CutsJson> getPulp(){
        TypeJson terror = result.getResult().getScenes().getPulp();
        if(!terror.getCuts().isEmpty()){
            return terror.getCuts();
        }

        CutsJson cutsJson = new CutsJson();
        cutsJson.setDetails(terror.getDetails());
        cutsJson.setSuggestion(terror.getSuggestion());
        return Collections.singletonList(cutsJson);

    }

}
