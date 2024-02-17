package com.example.imageclassifier.utils;

import android.graphics.RectF;

import androidx.annotation.NonNull;

public class Notifier {
    private float threshold_value = 0;
    private float current_centerX = 0.0f;
    private float current_centerY = 0.0f;
    private float current_size = 0.0f;
    private float prior_centerX = 0.0f;
    private float prior_centerY = 0.0f;
    private float prior_size = 0.0f;
    public Notifier(float threshold) {this.threshold_value = threshold;}

    public String nofiy_action(RectF bounding_box) {
        String output = "";
        current_centerX = bounding_box.centerX();
        current_centerY = bounding_box.centerY();
        current_size = bounding_box.width() * bounding_box.height();

        float horizontal_diff = current_centerX - prior_centerX;
        float vertical_diff = current_centerY - prior_centerY;
        
        // 수평으로 이동한 거리가 더 크고, threshold value 를 넘었을 경우 (물체가 왼쪽에 있음.)
        if((horizontal_diff >= vertical_diff) && (horizontal_diff > this.threshold_value)) {
            output = "On your left and ";
        }
        // 수평으로 이동한 거리가 더 크고, - threshold value 를 넘었을 경우 (믈체가 오른쪽에 있음.)
        else if((horizontal_diff >= vertical_diff) && (horizontal_diff < -(this.threshold_value))) {
            output = "On your right and ";
        }
        // 수직으로 이동한 거리가 더 크고, threshold value 를 넘었을 경우 (물체가 위에 있음.)
        else if((horizontal_diff < vertical_diff) && (vertical_diff > this.threshold_value)) {
            output = "On your top and ";
        }
        else if((horizontal_diff < vertical_diff) && (vertical_diff <= -(this.threshold_value))) {
            output = "On your bottom and ";
        }

        if(current_size < prior_size) {
            output.concat("Closer");
        }

        return output;
    }

    public void set_current_prior() {
        this.prior_centerX = this.current_centerX;
        this.prior_centerY = this.current_centerY;
        this.prior_size = this.current_size;
    }
}
