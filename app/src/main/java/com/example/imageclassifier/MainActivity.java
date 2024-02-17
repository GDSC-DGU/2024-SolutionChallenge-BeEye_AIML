package com.example.imageclassifier;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Vibrator;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.app.Fragment;

import com.example.imageclassifier.camera.CameraFragment;
import com.example.imageclassifier.tflite.Detector;
import com.example.imageclassifier.utils.YuvToRgbConverter;

import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.vision.detector.Detection;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "[IC]MainActivity";
    public String wantedObject = " ";
    private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;
    private static final int PERMISSION_REQUEST_CODE = 1;

    private TextView textView;
    private TextView editText;
    private TextView clostText;
    private Button button;
    private Vibrator vibrator;
    private Detector dtr;

    private int previewWidth = 0;
    private int previewHeight = 0;
    private int sensorOrientation = 0;

    private Bitmap rgbFrameBitmap = null;

    private HandlerThread handlerThread;
    private Handler handler;

    private boolean isProcessingFrame = false;

    private List result_detected = null;
    private int maxResult = 3;

    private float current_centerX = 0.0f;
    private float current_centerY = 0.0f;
    private float current_width = 0.0f;
    private float prior_centerX = 0.0f;
    private float prior_centerY = 0.0f;
    private float prior_width = 0.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(null);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // UI 상에 있는 객체들을 불러오는 부분
        textView = findViewById(R.id.textView);
        editText = (EditText)findViewById(R.id.inputView);
        button = (Button)findViewById(R.id.button);
        clostText = findViewById(R.id.CloseText);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                wantedObject = editText.getText().toString();
            }
        });

        // 휴대폰 상의 진동 기능을 불러오는 vibrator
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // 물체를 탐지하는 class Detector, 객체 선언 및 초기화
        dtr = new Detector(this, maxResult);
        dtr.initDetector();

        // 카메라 접근 권한 획득
        if(checkSelfPermission(CAMERA_PERMISSION)
                == PackageManager.PERMISSION_GRANTED) {
            setFragment();
        } else {
            requestPermissions(new String[]{CAMERA_PERMISSION},
                    PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    protected synchronized void onDestroy() {
        // cls.finish();
        Log.v("onDestroy", "End");
        // 프로세스를 완전히 종료하도록 함.
        moveTaskToBack(true);
        finishAndRemoveTask();
        android.os.Process.killProcess(android.os.Process.myPid());
        super.onDestroy();
    }

    @Override
    public synchronized void onResume() {
        super.onResume();

        handlerThread = new HandlerThread("InferenceThread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public synchronized void onPause() {
        handlerThread.quitSafely();
        try {
            handlerThread.join();
            handlerThread = null;
            handler = null;
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }

        super.onPause();
    }

    @Override
    public synchronized void onStart() {
        super.onStart();
    }

    @Override
    public synchronized void onStop() {
        super.onStop();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == PERMISSION_REQUEST_CODE) {
            if(grantResults.length > 0 && allPermissionsGranted(grantResults)) {
                setFragment();
            } else {
                Toast.makeText(this, "permission denied", Toast.LENGTH_LONG).show();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private boolean allPermissionsGranted(final int[] grantResults) {
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    protected void setFragment() {
        // Size inputSize = cls.getModelInputSize();
        Size inputSize = new Size(320, 320);
        String cameraId = chooseCamera();

        if(inputSize.getWidth() > 0 && inputSize.getHeight() > 0 && !cameraId.isEmpty()) {
            Fragment fragment = CameraFragment.newInstance(
                    (size, rotation) -> {
                        previewWidth = size.getWidth();
                        previewHeight = size.getHeight();
                        sensorOrientation = rotation - getScreenOrientation();
                    },
                    reader->processImage(reader),
                    inputSize,
                    cameraId);

            getFragmentManager().beginTransaction().replace(
                    R.id.fragment, fragment).commit();
        } else {
            Toast.makeText(this, "Can't find camera", Toast.LENGTH_SHORT).show();
        }
    }

    private String chooseCamera() {
        final CameraManager manager =
                (CameraManager)getSystemService(Context.CAMERA_SERVICE);
        try {
            for (final String cameraId : manager.getCameraIdList()) {
                final CameraCharacteristics characteristics =
                        manager.getCameraCharacteristics(cameraId);

                final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    return cameraId;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return "";
    }

    /* 화면의 회전을 감지하고 현재 어느 각도인지 quantize value 를 return  */
    protected int getScreenOrientation() {
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_90:
                return 90;
            default:
                return 0;
        }
    }

    /*주요 동작이 벌어지는 함수*/
    protected void processImage(ImageReader reader) {
        // setFragment (GOTO : 159 번째 줄) -> previewWidth와 previewHeight 의 값 정해짐
        /*
         * previewWidth 와  previewHeight 가 0이라면 setFragment 가 제대로 되지 않은 것이므로 일단 return
         * */
        if (previewWidth == 0 || previewHeight == 0) {
            return;
        }

        // null 로 초기화된 rgbFrameBitmap에 실질적인 값을 대입해줌.
        // rgbFrameBitmap 에 Bitmap을 생성하여 넣어줌. (투명도가 있는 ARGB_8888)
        // 카메라로 얻은 이미지를 bitmap으로 변환. Detector의 메서드 detect의 input으로 넣어주기 위함.
        if(rgbFrameBitmap == null) {
            rgbFrameBitmap = Bitmap.createBitmap(
                    previewWidth,
                    previewHeight,
                    Bitmap.Config.ARGB_8888);
        }

        if (isProcessingFrame) {
            return;
        }

        isProcessingFrame = true;

        final Image image = reader.acquireLatestImage();
        if (image == null) {
            isProcessingFrame = false;
            return;
        }

        YuvToRgbConverter.yuvToRgb(this, image, rgbFrameBitmap);
        // runInBackground() : thread 실행 (272 line)
        runInBackground(() -> {
            if (dtr != null && dtr.isInitialized()) {
                result_detected = dtr.detectImage(rgbFrameBitmap, sensorOrientation);
                int result_len = result_detected.size();
                // UI를 변경하는 쓰레드, 여기서 UI 를 변경해야 함. 그렇지 않으면 오류
                runOnUiThread(() -> {
                    for (int idx = 0; idx < result_len; idx++) {
                        Detection dt = (Detection) result_detected.get(idx);
                        RectF rf = dt.getBoundingBox();
                        Category ct = dt.getCategories().get(0);

                        if(wantedObject.equals(ct.getLabel().toString())) {

                            current_centerX = rf.centerX();
                            current_centerY = rf.centerY();
                            current_width = rf.width() * rf.height();

                            // Log.v("object", " " + ct.getLabel().toString());
                            if((Math.abs(current_centerX - prior_centerX) > Math.abs(current_centerY - prior_centerY))
                            && Math.abs(current_centerX - prior_centerX) > 25.0f) {
                                if(current_centerX > 160.0) {
                                    textView.setText("Left");
                                }
                                else if(current_centerX <= 160.0) {
                                    textView.setText("Right");
                                }
                            }
                            else if((Math.abs(current_centerX - prior_centerX) <= Math.abs(current_centerY - prior_centerY))
                                    && Math.abs(current_centerY - prior_centerY) > 25.0f) {
                                if(current_centerY > 160.0) {
                                    textView.setText("Top");
                                }
                                else if(current_centerY <= 160.0) {
                                    textView.setText("Bottom");
                                }
                            }

                            if (current_width > previewHeight * previewWidth * 0.3) {
                                clostText.setText("Here!");
                                vibrator.vibrate(1000);
                            }
                            else {
                                clostText.setText("Near here but not yet");
                            }
                        }
                    }
                    prior_centerX = current_centerX;
                    prior_centerY = current_centerY;
                    prior_width = current_width;
                });
            }

            image.close();
            isProcessingFrame = false;
        });
    }

    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }
}