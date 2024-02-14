package com.example.imageclassifier.tflite;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.support.image.ImageOperator;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.Rot90Op;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;
import org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions;

import java.io.IOException;
import java.util.List;

public class Detector {
    public boolean isInitialize = false;

    private ObjectDetector objectDetector;
    private Context context;
    private float threshold = 0.5f;
    private int maxResult = 3;  // 한 이미지에서 탐지하고 싶은 객체의 최대 개수를 의미

    public Detector(Context context, int maxResult) {
        this.context = context;
        this.maxResult = maxResult;
    }

    // initDetector : Detector 를 초기화 해주는 함수
    public final void initDetector() {
        ObjectDetectorOptions options =
                ObjectDetectorOptions.builder()
                        .setBaseOptions(BaseOptions.builder().useGpu().build())
                        .setMaxResults(this.maxResult)
                        .setScoreThreshold(this.threshold)
                        .build();
        try {
            // assets 폴더에 있는 tflite (AI 모델) 파일을 불러와 ObjectDetector 객체를 생성 (ObjectDetector는 tensorflow에서 제공하는 클래스)
            objectDetector = ObjectDetector.createFromFileAndOptions(this.context, "efficient_det.tflite", options);
            isInitialize = true;
            Log.v("detector", objectDetector.toString());
        } catch(IOException e) {
            isInitialize = false;
            Log.e("error", e.toString());
        }
    }

    // detectImage : Bitmap 과 화면이 얼마나 회전되었는지를 나타내는 sensorOrientation 을 input 으로 받고, 객체 탐지의 결과를 list 로 반환
    public List detectImage(Bitmap image, int sensorOrientation) {
        ImageProcessor imageProcessor = new ImageProcessor.Builder()
                .add((ImageOperator) (new Rot90Op(sensorOrientation / 90)))
                .build();
        TensorImage tensorImage = imageProcessor.process(TensorImage.fromBitmap(image));
        List results = this.objectDetector.detect(tensorImage);

        if (results != null) {return results;}
        return null;
    }

    public boolean isInitialized() {return this.isInitialize;}
}
