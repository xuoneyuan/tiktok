package com.tiktok.util;

import com.tiktok.entity.vo.HotVideo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;

public class TopK {
    private int k;
    private Queue<HotVideo> queue;

    public TopK(int k,Queue<HotVideo>queue){
        this.k=k;
        this.queue=queue;
    }
    public void add(HotVideo hotVideo){
        if(queue.size()<k){
            queue.add(hotVideo);
        }
        else if(queue.peek().getHot()<hotVideo.getHot()){
            queue.poll();
            queue.add(hotVideo);
        }

    }
    public List<HotVideo> get(){
        ArrayList<HotVideo> hotVideos = new ArrayList<>(queue.size());
        while(!queue.isEmpty()){
            hotVideos.add(queue.poll());
        }
        Collections.reverse(hotVideos);
        return hotVideos;
    }

}
