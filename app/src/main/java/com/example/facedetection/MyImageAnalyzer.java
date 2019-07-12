package com.example.facedetection;

import android.graphics.Rect;
import android.util.Log;

import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.android.gms.tasks.Task;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class MyImageAnalyzer implements ImageAnalysis.Analyzer {
    private static final String TAG = MyImageAnalyzer.class.getSimpleName();


    private FirebaseVisionFaceDetectorOptions highAccuracyOpts;
    private AtomicBoolean isBusy = new AtomicBoolean(false);

    private int rotation;

    public MyImageAnalyzer(int rotation) {
        this.rotation = rotation;
        highAccuracyOpts =
                new FirebaseVisionFaceDetectorOptions.Builder()
                        .setPerformanceMode(FirebaseVisionFaceDetectorOptions.FAST)
                        .build();
    }

    @Override
    public void analyze(ImageProxy image, int rotationDegrees) {
        if (image.getImage() == null)
            return;

//        Log.d(TAG, "analyze: " + image.getImage().getWidth() + ":" + image.getImage().getHeight());

        int imageWidth = image.getImage().getWidth();
        int imageHeight = image.getImage().getHeight();

        if (isBusy.compareAndSet(false, true)) {

            FirebaseVisionImage visionImage = FirebaseVisionImage.fromMediaImage(image.getImage(), this.rotation);
            FirebaseVisionFaceDetector detector = FirebaseVision.getInstance()
                    .getVisionFaceDetector(highAccuracyOpts);

            Task<List<FirebaseVisionFace>> result = detector.detectInImage(visionImage)
                    .addOnSuccessListener(faces -> {
                        Log.d(TAG, "analyze: " + faces.size());
                        isBusy.set(false);

                        List<Rect> faceRects = new ArrayList<>();
                        for (FirebaseVisionFace face : faces) {
                            Rect boundingBox = face.getBoundingBox();
                            faceRects.add(boundingBox);
                        }

                        if (faces.size() > 0) {

                            onDesiredImageFoundStatus(true, imageWidth, imageHeight, faceRects);
                        } else {
                            onDesiredImageFoundStatus(false, 0, 0, faceRects);
                        }
                    })
                    .addOnFailureListener(Throwable::printStackTrace);
        }
    }

    abstract void onDesiredImageFoundStatus(boolean isFound, int imageWidth, int imageHeight, List<Rect> faceRects);

}
